package com.ib.pki.service;

import com.ib.pki.model.ActivationToken;
import com.ib.pki.model.RecoverToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    private static final String FRONTEND_URL = "http://localhost:4200";
    private static final String activationRoute = FRONTEND_URL + "/activate/";
    private static final String activationRouteWithChallenge = FRONTEND_URL + "/activate-challenge/";
    private static final String recoverAccountRoute = FRONTEND_URL + "/recover-account/";

    public void sendActivationMail(String to, ActivationToken token) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("Activate your CA-USER IB-PROJECT account");
        mail.setText(
                "Welcome to IB-PROJECT!\n\n" +
                        "Your account is almost ready.\n" +
                        "Click the link below to activate it:\n\n"
                        + activationRoute + token.getToken() + "\n\n" +
                        "\n\nSee you there,\n Team 29"
        );
        mailSender.send(mail);
    }

    public void sendActivationMailWithChallenge(String to, ActivationToken token) throws UnsupportedEncodingException {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("Activate your USER IB-PROJECT account");
        mail.setText(
                "Welcome to IB-PROJECT!\n\n" +
                        "Your account is almost ready.\n" +
                        "Please click the link to activate your account and decrypt the challenge:\n\n"
                        + activationRouteWithChallenge + token.getToken() + "?challenge=" + URLEncoder.encode(token.getEncryptedChallengeBase64(), StandardCharsets.UTF_8) + "\n\n" +
                        "\n\nSee you there,\n Team 29"
        );
        mailSender.send(mail);
    }

    public void sendPasswordResetMail(String to, RecoverToken token) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(to);
        mail.setSubject("[IB-PROJECT] Account Recovery Request");
        mail.setText(
                "Hello,\n\n" +
                        "A request has been made to reset the password for your account.\n" +
                        "To proceed, please click the secure link below. You will be prompted to upload your Private Key (.pem) to locally decrypt the security challenge and set a new password.\n"
                        +"Recovery Link:\n\n"
                        + recoverAccountRoute + token.getToken() + "?challenge=" + URLEncoder.encode(token.getEncryptedChallengeBase64(), StandardCharsets.UTF_8) + "\n\n" +
                        "\n\nThis link is valid for the next 30 minutes.\n\n"+
                        "Regards,\n Team 29"
        );
        mailSender.send(mail);
    }
}
