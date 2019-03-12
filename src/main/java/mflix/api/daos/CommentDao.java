package mflix.api.daos;

import com.mongodb.MongoClientSettings;
import com.mongodb.ReadConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Comment;
import mflix.api.models.Critic;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Component
public class CommentDao extends AbstractMFlixDao {

  public static String COMMENT_COLLECTION = "comments";

  private MongoCollection<Comment> commentCollection;
  private MongoCollection<Critic> criticCollection;

  private CodecRegistry pojoCodecRegistry;

  private final Logger log;

  @Autowired
  public CommentDao(
          MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
    super(mongoClient, databaseName);
    log = LoggerFactory.getLogger(this.getClass());
    this.db = this.mongoClient.getDatabase(MFLIX_DATABASE);
    this.pojoCodecRegistry =
            fromRegistries(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    fromProviders(PojoCodecProvider.builder().automatic(true).build()));
    this.commentCollection =
            db.getCollection(COMMENT_COLLECTION, Comment.class).withCodecRegistry(pojoCodecRegistry);
    this.criticCollection =
            db.getCollection(COMMENT_COLLECTION, Critic.class).withCodecRegistry(pojoCodecRegistry);
  }

  /**
   * Returns a Comment object that matches the provided id string.
   *
   * @param id - comment identifier
   * @return Comment object corresponding to the identifier value
   */
  public Comment getComment(final String id) {
    return commentCollection.find(new Document("_id", new ObjectId(id))).first();
  }

  /**
   * Adds a new Comment to the collection. The equivalent instruction in the mongo shell would be:
   *
   * <p>db.comments.insertOne({comment})</p>
   *
   * @param comment - Comment object.
   * @throw IncorrectDaoOperation if the insert fails, otherwise
   * returns the resulting Comment object.
   */
  public Comment addComment(final Comment comment) {
    if (comment.getId() != null) {
      final Bson filter = Filters.eq("_id", comment.getId());
      final Comment existingComment = commentCollection.find(filter).limit(1).first();
      if (existingComment != null) {
        return existingComment; // TODO May be update old comment?
      }
    } else {
      throw new IncorrectDaoOperation("Please fill _id in comment");
    }

    try {
      commentCollection.insertOne(comment);
    } catch (Throwable t) {
      log.error(t.getLocalizedMessage(), t);
    }
    return comment;
  }

  /**
   * Updates the comment text matching commentId and user email. This method would be equivalent to
   * running the following mongo shell command:
   *
   * <p>db.comments.update({_id: commentId}, {$set: { "text": text, date: ISODate() }})
   *
   * <p>
   *
   * @param commentId - comment id string value.
   * @param text      - comment text to be updated.
   * @param email     - user email.
   * @return true if successfully updates the comment text.
   */
  public boolean updateComment(final String commentId, final String text, final String email) {
    if (commentId == null || email == null) {
      return false;
    }

    try {
      final Bson filter = Filters.eq("_id", new ObjectId(commentId));
      final Comment comment = commentCollection.find(filter).limit(1).first();
      if (comment != null) {
        if (email.equals(comment.getEmail())) {
          final Bson update = Updates.combine(
                  Updates.set("text", text),
                  Updates.set("date", new Date())
          );
          final UpdateResult updateResult = commentCollection.updateOne(filter, update);
          return updateResult.wasAcknowledged() && updateResult.getModifiedCount() == 1;
        }
      }
    } catch (Throwable t) {
      log.error(t.getLocalizedMessage(), t);
    }
    return false;
  }

  /**
   * Deletes comment that matches user email and commentId.
   *
   * @param commentId - commentId string value.
   * @param email     - user email value.
   * @return true if successful deletes the comment.
   */
  public boolean deleteComment(final String commentId, final String email) {
    if (commentId == null || "".equals(commentId)) {
      throw new IllegalArgumentException();
    }

    if (email == null || "".equals(email)) {
      return false;
    }

    try {
      final Bson filter = Filters.and(
              Filters.eq("_id", new ObjectId(commentId)),
              Filters.eq("email", email));
      final DeleteResult deleteResult = commentCollection.deleteOne(filter);
      return deleteResult.wasAcknowledged() && deleteResult.getDeletedCount() == 1;
    } catch (Throwable t) {
      log.error(t.getLocalizedMessage(), t);
    }
    return false;
  }

  /**
   * Ticket: User Report - produce a list of users that comment the most in the website. Query the
   * `comments` collection and group the users by number of comments. The list is limited to up most
   * 20 commenter.
   *
   * @return List {@link Critic} objects.
   */
  public List<Critic> mostActiveCommenters() {
    final int limit = 20;
    List<Critic> mostActive = new ArrayList<>(limit);
    // // TODO> Ticket: User Report - execute a command that returns the
    // // list of 20 users, group by number of comments. Don't forget,
    // // this report is expected to be produced with an high durability
    // // guarantee for the returned documents. Once a commenter is in the
    // // top 20 of users, they become a Critic, so mostActive is composed of
    // // Critic objects.
    final Bson group = Aggregates.group("$email", Accumulators.sum("count", 1));
    final Bson sort = Aggregates.sort(Sorts.descending("count"));
    final Bson limits = Aggregates.limit(limit);
    final List<Bson> pipeline = Arrays.asList(group, sort, limits);
    criticCollection.withReadConcern(ReadConcern.MAJORITY).aggregate(pipeline).into(mostActive);
    return mostActive;
  }
}