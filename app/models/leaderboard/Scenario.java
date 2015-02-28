package models.leaderboard;

import javax.persistence.*;
import play.data.validation.Required;
import play.db.jpa.Model;

@Entity
public class Scenario extends Model {

	@Required
	public String name;

	@Required
	@ManyToOne
	public Game game;

	@Override
	public String toString() {
		return name;
	}

	public enum Id {
		HighSpeed(1),
		Toyland(2),
		Artic(3),
		Desertic(4),
		Steam(5);
		private final long value;
		private Id(long value) {
			this.value = value;
		}
		public static Id fromValue(long value) {
			for(Id id : Id.values()) {
				if(id.value == value) return id;
			}
			return null;
		}
		public long longValue() {
			return value;
		}
	}
	public String getImage() {
		switch (Id.fromValue(this.id)) {
			case HighSpeed:
				return "/images/openttd/highSpeed_on.gif";
			case Toyland:
				return "/images/openttd/toyland_on.gif";
			case Artic:
				return "/images/openttd/artic_on.gif";
			case Desertic:
				return "/images/openttd/desertic_on.gif";
			case Steam:
				return "/images/openttd/steam_on.gif";
			default:
				return "/images/openttd/openttdLogo.png";
		}
	}

	public String getTrainImage() {
		switch (Id.fromValue(this.id)) {
			default:
			case HighSpeed:
				return "/images/openttd/high_speed_train.gif";
			case Toyland:
				return "/images/openttd/toyland_train.gif";
			case Artic:
				return "/images/openttd/artic_train.gif";
			case Desertic:
				return "/images/openttd/desertic_train.gif";
			case Steam:
				return "/images/openttd/steam_train.gif";
		}
	}
}
