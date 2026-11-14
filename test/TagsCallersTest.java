import java.util.ArrayList;
import java.util.List;

import org.junit.*;

import play.cache.Cache;
import play.db.jpa.JPA;
import play.mvc.Http.Response;
import play.templates.JavaExtensions;

import models.forum.Attachment;
import models.forum.Post;
import models.forum.Topic;
import models.forum.User;
import models.forum.UserGroup;

/**
 * The same tags, reached through the templates that call them, so the caller side of each tag
 * variable is covered too:
 *   - showPost.html: #{showPost post /} in forum/Topics/show.html takes the false side of
 *     #{if _showTopic}, and #{showPost arg:post, showTopic:true /} in forum/Users/show.html takes
 *     the true side, reading _caller.post from the enclosing #{list ... as:'post'} loop.
 *   - attachments.html: reached through showPost on a topic page, not only through showNews.
 *   - pluralize.html: the thousands grouping of ${_size.format('###,###')} on forum/Users/index.html,
 *     which needs at least 1000 members.
 *   - pagination.html: nbPages > 5 on forum/Users/index.html, whose page count is driven by the
 *     number of members rather than by the literal size:10 of Application/index.html.
 *   - forum/Users/show.html: #{if user.recentsGames} and #{if user.recentsPosts} in both directions.
 */
public class TagsCallersTest extends BaseFunctionalTest {

	@Test
	public void topicPageRendersShowPostWithoutTheTopicLink() throws Throwable {
		Response response = getPage("/forums/1/topics/1");
		assertIsOk(response);
		String body = getContent(response);

		// the #{else} side of #{if _showTopic}: forum/Topics/show.html calls #{showPost post /}
		assertNotContains("Posted in", body);
		assertContains("<a href=\"/users/3\">Bob</a>", body);
		assertContains("First post of the normal topic.", body);
		// pluralize of the poster's post count: bob has one post, modo has three
		assertContains("1 post", body);
		assertContains("3 posts", body);
		assertContains("<span class=\"pull-right\">01/01, 2024 ", body);
		assertContains("<span class=\"pull-right\">01/02, 2024 ", body);
		// no attachment on any of the fixture posts -> attachments' empty branch
		assertNotContains("<div class=\"well\">", body);
		assertNotContains("glyphicon glyphicon-file", body);
	}

	@Test
	public void topicPageRendersTheAttachmentBlockOfAReply() throws Throwable {
		inTransaction(() -> seedReplyWithAttachment("modo@domain.com"));
		String body = getContent(getPage("/forums/1/topics/1"));

		assertContains("<div class=\"well\">", body);
		assertContains("<span class=\"glyphicon glyphicon-file\"></span>", body);
		assertContains(">report.txt</a>", body);
		assertContains("2 downloads", body);
		assertContains("&lt;em&gt;x&lt;/em&gt;", body); // nl2br escapes it, raw() keeps the break real
		assertContains("First line<br/>Second line", body);
		assertNotContains("Posted in", body);
	}

	@Test
	public void userPageRendersShowPostWithItsTopicLink() throws Throwable {
		inTransaction(() -> seedReplyWithAttachment("bob@domain.com"));
		Response response = getPage("/users/3");
		assertIsOk(response);
		String body = getContent(response);

		// the #{if} side: forum/Users/show.html passes showTopic:true, and _caller.post is the loop
		// variable of #{list items:user.recentsPosts, as:'post'}
		assertContains("Posted in <a href=\"/forums/1/topics/1\">mainForum1 - Reply with attachments</a>", body);
		assertContains("<div class=\"well\">", body);
		assertContains(">report.txt</a>", body);
		assertContains(">Recents posts ...</h3>", body);
		// #{if user.recentsGames} false side: bob has no PlayedGameScore in this suite
		assertNotContains(">Recents games ...</h3>", body);
		// pluralize on the subtitle: 0 games, 2 posts, 1 topic
		assertContains("<p>0 games, 2 posts in 1 topic</p>", body);
	}

	@Test
	public void userPageWithoutPostsOrGamesOmitsBothSections() throws Throwable {
		final User quiet = inTransaction(() -> newQuietUser());
		Response response = getPage("/users/" + quiet.id);
		assertIsOk(response);
		String body = getContent(response);

		assertNotContains(">Recents posts ...</h3>", body);
		assertNotContains(">Recents games ...</h3>", body);
		assertNotContains("<div class=\"panel-body\">", body);
		assertNotContains("Posted in", body);
		// all three pluralize calls take the plural form of a zero count
		assertContains("<p>0 games, 0 posts in 0 topics</p>", body);
	}

	@Test
	public void membersIndexGroupsThousandsOfMembers() throws Throwable {
		final long nbUsers = inTransaction(() -> seedMembers(1000));
		String body = getContent(getPage("/users"));

		// ${_size.format('###,###')}: the grouping no other page reaches
		assertContains(JavaExtensions.format(nbUsers, "###,###") + " users", body);
		assertFalse("grouping expected: " + nbUsers, JavaExtensions.format(nbUsers, "###,###") == String.valueOf(nbUsers));
	}

	@Test
	public void membersIndexSkipsPagesOutsideTheWindowOfTheCurrentOne() throws Throwable {
		inTransaction(() -> seedMembers(1000));
		String body = getContent(getPage("/users?page=60"));
		assertContains("<li class=\"active\"><a href=\"#\">60</a></li>", body);
		assertContains("<li><a href=\"/users?page=1\">1</a></li>", body); // i == 1
		assertContains("<li><a href=\"/users?page=58\">58</a></li>", body); // Math.abs(i - _page) <= 2
		assertContains("<li><a href=\"/users?page=62\">62</a></li>", body); // Math.abs(i - _page) <= 2
		assertContains("<li><a href=\"/users?page=65\">65</a></li>", body); // i % 5 == 0
		assertContains("<li><a href=\"/users?page=101\">101</a></li>", body); // i == nbPages
		assertNotContains("<li><a href=\"/users?page=57\">57</a></li>", body);
		assertNotContains("<li><a href=\"/users?page=63\">63</a></li>", body);
		assertNotContains("<li><a href=\"/users?page=3\">3</a></li>", body);
	}

	// ~~~~~~~~~~~~ fixtures helpers

	/** A reply on topic 1 with a two-line content and one attachment. */
	private Post seedReplyWithAttachment(String email) {
		Post post = new Post();
		post.topic = Topic.findById(1L);
		post.postedBy = User.findByEmail(email);
		post.subject = "Reply with attachments";
		post.content = "First line\nSecond line <em>x</em>";
		post.postedAt = daysAgo(3);
		post.create();

		Attachment attachment = new Attachment();
		attachment.post = post;
		attachment.user = post.postedBy;
		attachment.filename = "report.txt";
		attachment.realfilename = "report.txt";
		attachment.mimetype = "text/plain";
		attachment.description = "Attachment report";
		attachment.downloads = 2;
		attachment.size = 12;
		attachment.at = daysAgo(3);
		attachment.create();
		return post;
	}

	/** A member with no post, no topic and no game: both #{if} sections of Users/show stay empty. */
	private User newQuietUser() {
		User user = new User();
		user.email = "quiet@domain.com";
		user.passwordHash = "5ebe2294ecd0e0f08eab7690d2a6ee69";
		user.name = "Quiet Member";
		user.group = UserGroup.findByName("General");
		user.active = Boolean.TRUE;
		user.confirmed = Boolean.TRUE;
		user.create();
		return user;
	}

	/**
	 * Bulk members so that forum/Users/index.html reaches the thousands grouping and nbPages > 5.
	 * User.countActive() only counts members that posted or played a game, so each member gets a
	 * post on topic 1; without one it would stay invisible on the Members page.
	 */
	private long seedMembers(int count) {
		UserGroup general = UserGroup.findByName("General");
		Topic topic = Topic.findById(1L);
		for (int i = 0; i < count; i++) {
			User user = new User();
			user.email = String.format("member%04d@domain.com", i);
			user.passwordHash = "5ebe2294ecd0e0f08eab7690d2a6ee69";
			user.name = String.format("Member %04d", i);
			user.group = general;
			user.active = Boolean.TRUE;
			user.confirmed = Boolean.TRUE;
			user.create();

			Post post = new Post();
			post.topic = topic;
			post.postedBy = user;
			post.subject = String.format("Post %04d", i);
			post.content = "A post by a bulk member.";
			post.postedAt = daysAgo(4);
			post.create();
		}
		JPA.em().flush();
		return User.countActive();
	}

	private static java.util.Date daysAgo(int days) {
		java.util.Calendar calendar = java.util.Calendar.getInstance();
		calendar.add(java.util.Calendar.DAY_OF_MONTH, -days);
		return calendar.getTime();
	}

	// ~~~~~~~~~~~~ request helpers

	/**
	 * GET with the caches cleared, as the other page tests do.
	 * Named getPage() on purpose: a static one-argument get(String) would be picked up by Groovy as
	 * a generic property getter on the Class object, and the test runner UI reads ${test.name} on
	 * every test class (see {module:testrunner}/app/views/TestRunner/index.html).
	 */
	private static Response getPage(String url) {
		Cache.clear();
		return GET(url);
	}

	private static <T> T inTransaction(java.util.function.Supplier<T> work) throws Throwable {
		return JPA.withTransaction(JPA.DEFAULT, false, () -> work.get());
	}

	private static void assertContains(String needle, String body) {
		assertTrue("expected to find '" + needle + "'", body.contains(needle));
	}

	private static void assertNotContains(String needle, String body) {
		assertFalse("expected not to find '" + needle + "'", body.contains(needle));
	}
}
