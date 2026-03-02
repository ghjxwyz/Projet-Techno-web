package pharmacie.rest;

import java.util.HashMap;
import java.util.Map;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailController {

    @Value("${sendgrid.api-key}")
    private String sendGridApiKey;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    @PostMapping("/send-email")
    public ResponseEntity<Map<String, String>> sendEmail(@RequestBody EmailRequest emailRequest) {
        Map<String, String> response = new HashMap<>();
        try {
            Email from = new Email(fromEmail);
            String subject = emailRequest.getSubject();
            Email to = new Email(emailRequest.getTo());
            Content content = new Content("text/plain", emailRequest.getBody());
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = new SendGrid(sendGridApiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response sendGridResponse = sg.api(request);

            if (sendGridResponse.getStatusCode() >= 200 && sendGridResponse.getStatusCode() < 300) {
                response.put("message", "Email sent successfully to " + emailRequest.getTo());
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to send email via SendGrid: " + sendGridResponse.getBody());
                return ResponseEntity.status(sendGridResponse.getStatusCode()).body(response);
            }
        } catch (Exception e) {
            response.put("error", "Failed to send email: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    static class EmailRequest {
        private String to;
        private String subject;
        private String body;

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}
