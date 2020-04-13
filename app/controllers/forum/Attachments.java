package controllers.forum;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import controllers.Application;
import controllers.Check;
import controllers.Profile;

import models.forum.Attachment;
import play.Play;
import play.mvc.With;

@With(Profile.class)
public class Attachments extends Application {
	
	static String attachmentsPath = Play.configuration.getProperty("forum.attachments.path", "/home/edlefou/www/upload");

	@Check(Profile.REPLYER)
	public static void download(Long id) throws IOException {
		Attachment attachment = Attachment.findById(id);
		notFoundIfNull(attachment);
		File file = new File(attachmentsPath + File.separator + attachment.realfilename);
		if(file != null) {
			FileInputStream fis = new FileInputStream(file);
			response.setContentTypeIfNotSet(attachment.mimetype);
			response.setHeader("Content-Disposition", "attachment;filename=" + attachment.filename);
			byte [] buffer = new byte [64 * 1024];
			while(fis.read(buffer) > 0) {
				response.out.write(buffer);
			}
			response.out.flush();
			response.out.close();
			fis.close();
		}
	}
}
