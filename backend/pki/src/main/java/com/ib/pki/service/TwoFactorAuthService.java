package com.ib.pki.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.ib.pki.model.User;
import com.ib.pki.util.CryptoUtil;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class TwoFactorAuthService {
    private final static String ISSUER = "IB-PKI-System";
    private final OrganizationKeyService organizationKeyService;
    private final GoogleAuthenticator googleAuthenticator;

    public TwoFactorAuthService(OrganizationKeyService organizationKeyService) {
        this.organizationKeyService = organizationKeyService;
        this.googleAuthenticator = new GoogleAuthenticator();
    }

    public String generateQRUrl(User user) {
        if(user.getOrganization() == null){
            throw new RuntimeException("User must belong to an organization to enable 2FA");
        }
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        String rawSecret = key.getKey();
        try{
            byte[] organizationKeyBytes = organizationKeyService.getOrganizationKeyBytes(user.getOrganization());
            String encryptedSecret = CryptoUtil.encrypt(rawSecret.getBytes(), organizationKeyBytes);
            user.setTwoFactorSecret(encryptedSecret);
            String url = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(ISSUER, user.getEmail(), new GoogleAuthenticatorKey.Builder(rawSecret).build());
            return convertUrlToQrBase64(url);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt and setup 2FA secret", e);
        }
    }

    private String convertUrlToQrBase64(String qrCodeText) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix bitMatrix = qrCodeWriter.encode(qrCodeText, BarcodeFormat.QR_CODE, 250, 250, hints);
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", outputStream);
            byte[] imageBytes = outputStream.toByteArray();

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            return "data:image/png;base64," + base64Image;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code image", e);
        }
    }

    public boolean verifyCode(User user, int code) {
        if (user.getTwoFactorSecret() == null || user.getOrganization() == null) {
            return false;
        }
        try {
            byte[] organizationKeyBytes = organizationKeyService.getOrganizationKeyBytes(user.getOrganization());
            byte[] secretBytes = CryptoUtil.decrypt(user.getTwoFactorSecret(), organizationKeyBytes);
            String rawSecret = new String(secretBytes, StandardCharsets.UTF_8);
            return googleAuthenticator.authorize(rawSecret, code);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt and verify 2FA code", e);
        }
    }



}