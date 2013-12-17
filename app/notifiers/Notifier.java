package notifiers;

import play.mvc.*;

import javax.mail.internet.*;

import models.forum.User;

public class Notifier extends Mailer {

    public static boolean welcome(User user) throws Exception {
        setFrom(new InternetAddress("root@strategyboard.net", "Strategy Board Notifier"));
        setReplyTo(new InternetAddress("root@strategyboard.net", "Do not reply"));
        setSubject("Welcome %s", user.name);
        addRecipient(user.email, new InternetAddress("root@strategyboard.net", "New users notice"));
        return sendAndWait(user);
    }
    
    public static boolean sendPassword(User user, String password) throws Exception {
        setFrom(new InternetAddress("root@strategyboard.net", "Strategy Board Notifier"));
        setReplyTo(new InternetAddress("root@strategyboard.net", "Do not reply"));
        setSubject("Reset password %s", user.name);
        addRecipient(user.email, new InternetAddress("root@strategyboard.net", "New users notice"));
        return sendAndWait(user, password);
    }
    
}
