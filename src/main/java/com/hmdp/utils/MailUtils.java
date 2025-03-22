package com.hmdp.utils;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.*;


public class MailUtils {
    public static void main(String[] args) throws MessagingException {
        sendToMail("619403961@qq.com", new MailUtils().achieveCode());
    }

    public static void sendToMail(String email, String code) throws MessagingException {
        // 创建一个Properties类用于记录邮箱的一些属性
        Properties props = new Properties();
        // 表示SMTP发送邮件，必须进行身份验证
        props.put("mail.smtp.auth", "true");
        //此处填写SMTP服务器
        props.put("mail.smtp.host", "smtp.qq.com");
        //端口号，QQ邮箱端口587
        props.put("mail.smtp.port", "465");
        // 启用 STARTTLS
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        // 此处填写，写信人的账号
        props.put("mail.user", "619403961@qq.com");
        // 此处填写16位STMP口令
        props.put("mail.password", "onsltokidxdrbeac");

        Authenticator authenticator = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(
                        props.getProperty("mail.user"),
                        props.getProperty("mail.password")
                );
            }
        };
        Session mailSession = Session.getInstance(props, authenticator);
        MimeMessage message = new MimeMessage(mailSession);
        message.setFrom(new InternetAddress(props.getProperty("mail.user")));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(email));
        message.setSubject("验证码");
        message.setText("尊敬的用户:你好!\n注册验证码为:" + code + "(有效期为一分钟,请勿告知他人)");
        Transport.send(message);
    }

    public static String achieveCode() {
        String[] beforeShuffle = new String[]{
                "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F",
                "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z", "a",
                "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v",
                "w", "x", "y", "z"
        };
        List<String> list = Arrays.asList(beforeShuffle);
        Collections.shuffle(list);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(list.get(i));
        }
        return sb.toString();
    }
}
