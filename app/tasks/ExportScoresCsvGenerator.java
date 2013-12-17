package tasks;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import models.leaderboard.PlayedGameScore;

public class ExportScoresCsvGenerator {

	private final Iterator<PlayedGameScore> iterator;

	public ExportScoresCsvGenerator(List<PlayedGameScore> collection) {
		this.iterator = collection.iterator();
	}

	public boolean hasMoreData() {
		return iterator.hasNext();
	}

	public Future<String> nextDataChunk() {
		FutureTask<String> future = new FutureTask<String>(new Callable<String>() {
			public String call() {
				return exportAsCsv(iterator.next());
			}
		});
		return future;
	}

	private String exportAsCsv(PlayedGameScore score) {
		StringBuilder builder = new StringBuilder();
		builder.append(score.id);
		return builder.toString();
	}
}
