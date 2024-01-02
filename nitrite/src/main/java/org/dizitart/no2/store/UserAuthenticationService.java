/*
 * Copyright (c) 2017-2021 Nitrite author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.dizitart.no2.store;

import lombok.extern.slf4j.Slf4j;
import org.dizitart.no2.common.util.SecureString;
import org.dizitart.no2.exceptions.NitriteSecurityException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import static org.dizitart.no2.common.Constants.*;
import static org.dizitart.no2.common.util.StringUtils.isNullOrEmpty;

/**
 * @author Anindya Chatterjee
 * @since 4.0
 */
@Slf4j
public class UserAuthenticationService {
    private static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String OLD_HASH_ALGORITHM = "PBKDF2WithHmacSHA1";
    private final SecureRandom random;
    private final NitriteStore<?> store;

    public UserAuthenticationService(NitriteStore<?> store) {
        this.store = store;
        this.random = new SecureRandom();
    }

    public void authenticate(String username, String password) {
        boolean existing = store.hasMap(USER_MAP);
        if (!isNullOrEmpty(password) && !isNullOrEmpty(username)) {
            if (!existing) {
                byte[] salt = getNextSalt();
                byte[] hash = hash(password.toCharArray(), salt, HASH_ALGORITHM);
                UserCredential userCredential = new UserCredential();
                userCredential.setPasswordHash(hash);
                userCredential.setPasswordSalt(salt);

                NitriteMap<String, UserCredential> userMap = store.openMap(USER_MAP, String.class, UserCredential.class);
                userMap.put(username, userCredential);
            } else {
                NitriteMap<String, UserCredential> userMap = store.openMap(USER_MAP, String.class, UserCredential.class);
                UserCredential userCredential = userMap.get(username);

                if (userCredential != null) {
                    byte[] salt = userCredential.getPasswordSalt();
                    byte[] expectedHash = userCredential.getPasswordHash();

                    if (notExpectedPassword(password.toCharArray(), salt, expectedHash, HASH_ALGORITHM)) {
                        // try to authenticate with old algorithm
                        if (notExpectedPassword(password.toCharArray(), salt, expectedHash, OLD_HASH_ALGORITHM)) {
                            throw new NitriteSecurityException("Username or password is invalid");
                        }
                    }
                } else {
                    throw new NitriteSecurityException("Username or password is invalid");
                }
            }
        } else if (existing) {
            throw new NitriteSecurityException("Username or password is invalid");
        }
    }

    public void addOrUpdatePassword(boolean update, String username, SecureString oldPassword, SecureString newPassword) {
        NitriteMap<String, UserCredential> userMap = null;

        if (update) {
            userMap = store.openMap(USER_MAP, String.class, UserCredential.class);
            UserCredential credential = userMap.get(username);

            if (credential != null) {
                byte[] salt = credential.getPasswordSalt();
                byte[] expectedHash = credential.getPasswordHash();

                if (notExpectedPassword(oldPassword.asString().toCharArray(), salt, expectedHash, HASH_ALGORITHM)) {
                    throw new NitriteSecurityException("Username or password is invalid");
                }
            } else {
                // if credential is null, it means the user is not present, so we cannot update
                throw new NitriteSecurityException("Username or password is invalid");
            }
        } else {
            if (store.hasMap(USER_MAP)) {
                throw new NitriteSecurityException("Cannot add new credentials");
            }
        }

        if (userMap == null) {
            userMap = store.openMap(USER_MAP, String.class, UserCredential.class);
        }

        byte[] salt = getNextSalt();
        byte[] hash = hash(newPassword.asString().toCharArray(), salt, HASH_ALGORITHM);

        UserCredential userCredential = new UserCredential();
        userCredential.setPasswordHash(hash);
        userCredential.setPasswordSalt(salt);
        userMap.put(username, userCredential);
    }

    private byte[] getNextSalt() {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

    private byte[] hash(char[] password, byte[] salt, String algorithm) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, HASH_ITERATIONS, HASH_KEY_LENGTH);
        Arrays.fill(password, Character.MIN_VALUE);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance(algorithm);
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            log.error("Error while hashing password", e);
            throw new NitriteSecurityException("Error while hashing a password: " + e.getMessage());
        } finally {
            spec.clearPassword();
        }
    }

    private boolean notExpectedPassword(char[] password, byte[] salt, byte[] expectedHash, String algorithm) {
        byte[] pwdHash = hash(password, salt, algorithm);
        Arrays.fill(password, Character.MIN_VALUE);
        if (pwdHash.length != expectedHash.length) return true;
        for (int i = 0; i < pwdHash.length; i++) {
            if (pwdHash[i] != expectedHash[i]) return true;
        }
        return false;
    }
}
