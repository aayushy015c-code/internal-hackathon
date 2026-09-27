package com.hackathon.distress.service;

import com.hackathon.distress.entity.AppConfig;
import com.hackathon.distress.entity.AppUser;
import com.hackathon.distress.repository.AlertRepository;
import com.hackathon.distress.repository.AppConfigRepository;
import com.hackathon.distress.repository.AppUserRepository;
import com.hackathon.distress.repository.ContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// Who is using the app.
//
// There are no passwords or accounts. The first time a browser opens the app it
// registers and gets:
//   - a call ID (public, permanent): what other people type to call you
//   - an access key (secret): the browser saves it and sends it with every
//     request in the "X-User-Key" header. That's how we know whose data to use.
// The call ID is in the database, so it stays the same after refreshes,
// browser restarts and Docker restarts. To use the same identity in another
// browser, enter the access key there (Settings > Your identity).
@Service
public class UserService {

    public static final String HEADER = "X-User-Key";

    private static final Logger audit = LoggerFactory.getLogger("audit");
    // no 0/O, 1/I/L, so IDs are easy to read out loud and type
    private static final String ID_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private final AppUserRepository userRepo;
    private final ContactRepository contactRepo;
    private final AlertRepository alertRepo;
    private final AppConfigRepository configRepo;
    private final SecureRandom random = new SecureRandom();

    public UserService(AppUserRepository userRepo, ContactRepository contactRepo,
                       AlertRepository alertRepo, AppConfigRepository configRepo) {
        this.userRepo = userRepo;
        this.contactRepo = contactRepo;
        this.alertRepo = alertRepo;
        this.configRepo = configRepo;
    }

    public record NewUser(String callId, String accessKey) {}

    @Transactional
    public NewUser register() {
        boolean firstUser = userRepo.count() == 0;

        String callId;
        do {
            callId = newCallId();
        } while (userRepo.existsByCallId(callId));

        byte[] keyBytes = new byte[32];
        random.nextBytes(keyBytes);
        String accessKey = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);

        AppUser user = new AppUser();
        user.setCallId(callId);
        user.setKeyHash(sha256(accessKey));
        user = userRepo.save(user);

        if (firstUser) {
            claimDataFromBeforeUsersExisted(user.getId());
        }
        audit.info("user {} registered", user.getId());
        return new NewUser(callId, accessKey);
    }

    // Returns the user for this access key, or answers 401 if it's missing or wrong.
    public AppUser require(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No access key");
        }
        return userRepo.findByKeyHash(sha256(accessKey.trim()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown access key"));
    }

    // Before this feature there was one shared set of data (settings row id 1,
    // contacts and alerts without an owner). The first user to register keeps it,
    // so nobody loses what they already set up.
    private void claimDataFromBeforeUsersExisted(Long userId) {
        int contacts = contactRepo.claimUnowned(userId);
        int alerts = alertRepo.claimUnowned(userId);
        if (!userId.equals(1L) && !configRepo.existsById(userId)) {
            configRepo.findById(1L).ifPresent(old -> {
                AppConfig mine = new AppConfig(userId);
                BeanUtils.copyProperties(old, mine, "id");
                configRepo.save(mine);
                configRepo.delete(old);
            });
        }
        if (contacts + alerts > 0) {
            audit.info("user {} took over {} existing contacts and {} alerts", userId, contacts, alerts);
        }
    }

    private String newCallId() {
        StringBuilder id = new StringBuilder("SS");
        for (int group = 0; group < 3; group++) {
            id.append('-');
            for (int i = 0; i < 4; i++) {
                id.append(ID_ALPHABET.charAt(random.nextInt(ID_ALPHABET.length())));
            }
        }
        return id.toString(); // e.g. SS-K7P3-9QDM-X2WA (about 59 random bits)
    }

    static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
