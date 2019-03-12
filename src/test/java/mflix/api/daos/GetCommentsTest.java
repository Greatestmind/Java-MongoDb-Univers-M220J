package mflix.api.daos;

import com.mongodb.client.MongoClient;
import mflix.config.MongoDBConfiguration;
import org.bson.Document;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

@SpringBootTest(classes = {MongoDBConfiguration.class})
@EnableConfigurationProperties
@EnableAutoConfiguration
@RunWith(SpringJUnit4ClassRunner.class)
public class GetCommentsTest extends TicketTest {

  private MovieDao dao;
  @Autowired MongoClient mongoClient;

  @Value("${spring.mongodb.database}")
  String databaseName;

  @Before
  public void setUp() {
    this.dao = new MovieDao(mongoClient, databaseName);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void testGetMovieComments() {
    String movieId = "573a1390f29313caabcd418c";
    Document movieDocument = dao.getMovie(movieId);
    Assert.assertNotNull("Should not return null. Check getMovie()", movieDocument);

    List<Document> commentDocs = (List<Document>) movieDocument.get("comments");
    int expectedSize = 2;
    Assert.assertEquals(
        "Comments list size does not match expected", expectedSize, commentDocs.size());

    String expectedName = "Patricia Good";
    Assert.assertEquals(
        "Expected `name` field does match: check your " + "getMovie() comments sort order.",
        expectedName,
        commentDocs.get(1).getString("name"));
  }

  @Test
  public void testCommentsMovieIdNonExisting() {
    String nonExistingMovieId = "a73a1396559313caabc14181";

    Assert.assertNull(
        "Non-existing movieId should return null document. " + "Check your getMovie() method",
        dao.getMovie(nonExistingMovieId));
  }
}
