package com.miniide.settings;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

public class KeyVault {
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;

    private AgentKeysFile decryptedKeys = null;

    public boolean isUnlocked() {
        return decryptedKeys != null;
    }

    public void lock() {
        decryptedKeys = null;
    }

    public AgentKeysFile getDecryptedKeys() {
        return decryptedKeys;
    }

    public AgentKeysFile unlock(Path vaultPath, String password, ObjectMapper mapper) throws Exception {
        if (!Files.exists(vaultPath)) {
            decryptedKeys = new AgentKeysFile();
            return decryptedKeys;
        }

        EncryptedVaultFile vault = mapper.readValue(vaultPath.toFile(), EncryptedVaultFile.class);
        AgentKeysFile keys = decryptVault(vault, password, mapper);
        decryptedKeys = keys;
        return keys;
    }

    public void save(Path vaultPath, String password, ObjectMapper mapper, AgentKeysFile keys) throws Exception {
        EncryptedVaultFile vault = encryptVault(keys, password, mapper);
        Files.createDirectories(vaultPath.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(vaultPath.toFile(), vault);
    }

    private EncryptedVaultFile encryptVault(AgentKeysFile keys, String password, ObjectMapper mapper) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(salt);
        random.nextBytes(iv);

        SecretKey secretKey = deriveKey(password, salt, KEY_BITS, "PBKDF2WithHmacSHA256", 120000);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] plaintext = mapper.writeValueAsBytes(keys);
        byte[] ciphertext = cipher.doFinal(plaintext);

        EncryptedVaultFile vault = new EncryptedVaultFile();
        vault.setSalt(Base64.getEncoder().encodeToString(salt));
        vault.setIv(Base64.getEncoder().encodeToString(iv));
        vault.setCiphertext(Base64.getEncoder().encodeToString(ciphertext));
        return vault;
    }

    private AgentKeysFile decryptVault(EncryptedVaultFile vault, String password, ObjectMapper mapper) throws Exception {
        byte[] salt = Base64.getDecoder().decode(vault.getSalt());
        byte[] iv = Base64.getDecoder().decode(vault.getIv());
        byte[] ciphertext = Base64.getDecoder().decode(vault.getCiphertext());

        SecretKey secretKey = deriveKey(password, salt, KEY_BITS, vault.getKdf(), vault.getIterations());

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] plaintext = cipher.doFinal(ciphertext);
        return mapper.readValue(new String(plaintext, StandardCharsets.UTF_8), AgentKeysFile.class);
    }

    private SecretKey deriveKey(String password, byte[] salt, int keyBits, String kdf, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyBits);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(kdf);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
