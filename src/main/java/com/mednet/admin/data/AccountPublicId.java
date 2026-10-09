package com.mednet.admin.data;

import java.security.SecureRandom;

final class AccountPublicId {

    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private AccountPublicId() {
    }

    static String create(String accountType) {
        String prefix = switch (accountType) {
            case "PATIENT" -> "PT";
            case "PROVIDER" -> "PR";
            case "HOME_CARE" -> "HC";
            case "LABORATORY" -> "LB";
            case "ADMIN" -> "AD";
            case "SUPER_ADMIN" -> "SA";
            default -> throw new IllegalArgumentException("Unsupported account type");
        };
        StringBuilder id = new StringBuilder(prefix).append('-');
        for (int index = 0; index < 12; index++) {
            id.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
