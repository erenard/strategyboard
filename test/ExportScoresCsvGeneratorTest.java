import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import play.test.UnitTest;

import models.leaderboard.PlayedGameScore;
import tasks.ExportScoresCsvGenerator;

/**
 * Coverage of tasks/ExportScoresCsvGenerator, the chunk producer controllers/openttd/Scores.exportAsCSV()
 * drives through await(generator.nextDataChunk()).
 *
 * nextDataChunk() builds a FutureTask around a Callable that reads the next element of the iterator, but it
 * never runs it and never hands it to a thread: play.mvc.Controller.await(Future) blocks on the same object.
 * That is why GET /openttd.scores/exportAsCSV answers an empty text/csv body (the table is empty, so
 * hasMoreData() is false and the loop never reaches await) and why a table with rows would never finish:
 * hasMoreData() keeps reporting true, because only the Callable advances the iterator, so the same
 * unstarted task is awaited over and over. The assertions below pin that state without ever calling get()
 * on an unstarted task, which would block the test thread.
 *
 * exportAsCsv() is private, so it is reached by reflection. It emits score.id alone: no header line, no
 * separator, no newline, so the rows of a real export would be concatenated into one long number.
 */
public class ExportScoresCsvGeneratorTest extends UnitTest {

	private static PlayedGameScore scoreWithId(Long id) {
		PlayedGameScore score = new PlayedGameScore();
		// Model.id is a public @Id @GeneratedValue field, so a row that is never persisted still has a key
		score.id = id;
		return score;
	}

	/** exportAsCsv() is private, and only Scores.exportAsCSV() would call it through the Callable. */
	private static String exportAsCsv(PlayedGameScore score) throws Throwable {
		Method method = ExportScoresCsvGenerator.class.getDeclaredMethod("exportAsCsv", PlayedGameScore.class);
		method.setAccessible(true);
		return (String) method.invoke(new ExportScoresCsvGenerator(new ArrayList<PlayedGameScore>()), score);
	}

	@Test
	public void hasMoreDataIsFalseForAnEmptyCollection() {
		assertFalse(new ExportScoresCsvGenerator(new ArrayList<PlayedGameScore>()).hasMoreData());
	}

	@Test
	public void exportAsCsvEmitsOnlyTheIdOfTheScore() throws Throwable {
		assertEquals("12", exportAsCsv(scoreWithId(12L)));
		assertEquals("0", exportAsCsv(scoreWithId(0L)));
		// No header, no separator and no newline: StringBuilder.append(score.id) is the whole body
		assertEquals("4243", exportAsCsv(scoreWithId(42L)) + exportAsCsv(scoreWithId(43L)));
		// StringBuilder.append(Object) prints "null" for a row that has no key yet
		assertEquals("null", exportAsCsv(scoreWithId(null)));
	}

	/**
	 * The Future the generator returns is a FutureTask nobody has started: isDone() stays false, and
	 * await() in Scores.exportAsCSV() would wait forever.
	 */
	@Test
	public void nextDataChunkReturnsAFutureThatNothingHasStarted() {
		List<PlayedGameScore> scores = new ArrayList<PlayedGameScore>();
		scores.add(scoreWithId(7L));
		ExportScoresCsvGenerator generator = new ExportScoresCsvGenerator(scores);

		Future<String> chunk = generator.nextDataChunk();
		assertTrue("expected a FutureTask, got " + chunk.getClass(), chunk instanceof FutureTask);
		assertFalse("the task must not be completed, since nothing runs it", chunk.isDone());
		assertFalse("the task must not be cancelled", chunk.isCancelled());

		// Only the Callable inside the task advances the iterator, so the generator still has data
		assertTrue("hasMoreData() must stay true while the task is never run", generator.hasMoreData());
		Future<String> secondChunk = generator.nextDataChunk();
		assertTrue("the same element is offered again", generator.hasMoreData());
		assertFalse(secondChunk.isDone());
	}

	/** Running the task by hand shows what await() would have received, and consumes one element. */
	@Test
	public void runningTheTaskYieldsTheIdOfTheScoreTheIteratorPointsAt() throws Throwable {
		List<PlayedGameScore> scores = new ArrayList<PlayedGameScore>();
		scores.add(scoreWithId(7L));
		scores.add(scoreWithId(9L));
		ExportScoresCsvGenerator generator = new ExportScoresCsvGenerator(scores);

		FutureTask<String> chunk = (FutureTask<String>) generator.nextDataChunk();
		chunk.run();
		assertEquals("7", chunk.get());
		assertTrue(chunk.isDone());

		FutureTask<String> next = (FutureTask<String>) generator.nextDataChunk();
		next.run();
		assertEquals("9", next.get());
		assertFalse(generator.hasMoreData());
	}

	/** A collection with no element left: the Callable throws, and the task reports it as an ExecutionException. */
	@Test
	public void nextDataChunkOfAnExhaustedIteratorFailsWhenItIsRun() throws Throwable {
		FutureTask<String> chunk = (FutureTask<String>) new ExportScoresCsvGenerator(
				new ArrayList<PlayedGameScore>()).nextDataChunk();
		chunk.run();

		try {
			chunk.get();
			fail("expected the task to report the exhausted iterator");
		} catch (ExecutionException e) {
			assertTrue("expected a NoSuchElementException at the root, got " + e.getCause(),
					e.getCause() instanceof NoSuchElementException);
		}
		assertTrue("the failed task counts as done", chunk.isDone());
	}

	/**
	 * play.mvc.Controller.await(Future) is what Scores.exportAsCSV() calls. Its unstarted task is never
	 * resolved, so the private exportAsCsv() method of the generator is never reached either: the CSV body
	 * stays empty whatever the table holds.
	 */
	@Test
	public void theCallableNeverRunsOnItsOwn() throws Throwable {
		List<PlayedGameScore> scores = new ArrayList<PlayedGameScore>();
		scores.add(scoreWithId(5L));
		Future<String> chunk = new ExportScoresCsvGenerator(scores).nextDataChunk();

		// isDone() is the only safe probe: get() would block, and cancel() would hide the defect
		assertFalse(chunk.isDone());
		try {
			// Nothing runs the task, so a timed get() reports a timeout rather than a value
			chunk.get(1, TimeUnit.MILLISECONDS);
			fail("expected the task to time out, since nothing runs it");
		} catch (TimeoutException e) {
			assertFalse(chunk.isDone());
		} catch (ExecutionException e) {
			fail("the callable must never run: " + e.getCause());
		}
	}
}
