package mflix.api.daos;


import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import mflix.api.models.Session;
import mflix.api.models.User;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
public class UserDao extends AbstractMFlixDao {

  private final MongoCollection<User> usersCollection;
  private final MongoCollection<Session> sessionsCollection;

  private static final Logger logger = LoggerFactory.getLogger(UserDao.class);

  @Autowired
  public UserDao(
          MongoClient mongoClient, @Value("${spring.mongodb.database}") String databaseName) {
    super(mongoClient, databaseName);
    CodecRegistry pojoCodecRegistry =
            fromRegistries(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    fromProviders(PojoCodecProvider.builder().automatic(true).build()));

    usersCollection = db.getCollection("users", User.class).withCodecRegistry(pojoCodecRegistry);
    sessionsCollection = db.getCollection("sessions", Session.class).withCodecRegistry(pojoCodecRegistry);
  }

  /**
   * Inserts the `user` object in the `users` collection.
   *
   * @param user - User object to be added
   * @return True if successful, throw IncorrectDaoOperation otherwise.
   */
  public boolean addUser(final User user) {
    logger.debug("User = {}", user);
    if (user == null || user.isEmpty()) {
      throw new IncorrectDaoOperation("invalid user");
    }

    try {
      usersCollection.insertOne(user);
      return true;
    } catch (Exception e) {
      logger.error(e.getLocalizedMessage(), e);
      throw new IncorrectDaoOperation(e.getLocalizedMessage(), e);
    }
  }

  /**
   * Creates session using userId and jwt token.
   *
   * @param userId - user string identifier
   * @param jwt    - jwt string token
   * @return true if successful
   */
  public boolean createUserSession(final String userId, final String jwt) {
    if (userId == null || jwt == null) {
      return false;
    }

    if (getUser(userId) == null) {
      return false;
    }

    final Bson filter = Filters.eq("user_id", userId);
    final Session oldSession = sessionsCollection.find(filter).limit(1).first();
    if (oldSession == null) {
      final Session session = new Session(userId, jwt);
      logger.info("Creating new session = {}", session);
      sessionsCollection.insertOne(session);
      return true;
    }

//        logger.info("Old session found. Updating... = {}", oldSession);
//        final UpdateResult updateResult = sessionsCollection.updateOne(filter, Updates.set("jwt", jwt));
//        logger.info("Update result = {}", updateResult);
//        return updateResult.wasAcknowledged() && updateResult.getModifiedCount() == 1;
    logger.info("Old session found = {}", oldSession);
    return true;
    //TODO > Ticket: Handling Errors - implement a safeguard against
    // creating a session with the same jwt token.
  }

  /**
   * Returns the User object matching the an email string value.
   *
   * @param email - email string to be matched.
   * @return User object or null.
   */
  public User getUser(final String email) {
    final Bson filter = Filters.eq("email", email);
    User user = usersCollection.find(filter).limit(1).first();
    logger.debug("User = {}", user);
    return user;
  }

  /**
   * Given the userId, returns a Session object.
   *
   * @param userId - user string identifier.
   * @return Session object or null.
   */
  public Session getUserSession(final String userId) {
    final Bson filter = Filters.eq("user_id", userId);
    return sessionsCollection.find(filter).limit(1).first();
  }

  public boolean deleteUserSessions(final String userId) {
    final Bson filter = Filters.eq("user_id", userId);
    final DeleteResult deleteResult = sessionsCollection.deleteMany(filter);
    return deleteResult.wasAcknowledged();
  }

  /**
   * Removes the user document that match the provided email.
   *
   * @param email - of the user to be deleted.
   * @return true if user successfully removed
   */
  public boolean deleteUser(final String email) {
    boolean result = deleteUserSessions(email);
    if (result) {
      try {
        final Bson filter = Filters.eq("email", email);
        final DeleteResult deleteResult = usersCollection.deleteMany(filter);
        result = deleteResult.wasAcknowledged();
      } catch (Exception e) {
        logger.error(e.getLocalizedMessage(), e);
        result = false;
      }
    }
    return result;
  }

  /**
   * Updates the preferences of an user identified by `email` parameter.
   *
   * @param email           - user to be updated email
   * @param userPreferences - set of preferences that should be stored and replace the existing
   *                        ones. Cannot be set to null value
   * @return true if preferences get update, false in case of null userPreferences or unsuccessful
   * write.
   */
  public boolean updateUserPreferences(final String email, final Map<String, ?> userPreferences) {
    if (userPreferences == null) {
      throw new IncorrectDaoOperation("Argument 'userPreferences' cannot be null");
    }

    try {
      final Bson filter = Filters.eq("email", email);
      final Bson update = Updates.set("preferences", userPreferences);
      final UpdateResult updateResult = usersCollection.updateOne(filter, update);
      return updateResult.wasAcknowledged() && updateResult.getModifiedCount() == 1;
    } catch (Exception e) {
      logger.error(e.getLocalizedMessage(), e);
      return false;
    }
  }
}