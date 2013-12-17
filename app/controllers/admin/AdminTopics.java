package controllers.admin;

import controllers.CRUD;
import controllers.Check;
import controllers.Profile;
import controllers.Secure;
import models.forum.Topic;
import play.mvc.*;

@With(Secure.class)
@Check(Profile.ADMINISTRATOR)
@CRUD.For(Topic.class)
public class AdminTopics extends CRUD {
    
}