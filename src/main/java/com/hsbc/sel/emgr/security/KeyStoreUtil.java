package com.hsbc.sel.emgr.security;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KeyStoreUtil {

    private static final Logger log = LoggerFactory.getLogger(KeyStoreUtil.class);

    // Windows Credential Manager target name
    private static final String WIN_TARGET_PREFIX = "PFS_EMGR_";
    private static final java.util.regex.Pattern SAFE_CRED_NAME = java.util.regex.Pattern.compile("^[A-Za-z0-9_\\-]{1,64}$");

    private KeyStoreUtil() {
    }

    /**
     * 옵션 A: 별도 키 파일에서 읽기.
     * 경로 예: files/pfs-db.key
     * 파일 권한은 소유자 전용으로 설정하는 것을 권장.
     */
    public static String readKeyFromFile(String keyFilePath) {
        Path path = Paths.get(keyFilePath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Key file not found: " + keyFilePath);
        }
        try {
            String key = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
            if (key.isEmpty()) {
                throw new IllegalStateException("Key file is empty: " + keyFilePath);
            }
            return key;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read key file: " + keyFilePath, ex);
        }
    }

    /**
     * 옵션 A: 키 파일 생성 (Windows ACL로 현재 사용자 전용 권한 설정).
     */
    public static void writeKeyFile(String keyFilePath, String key) {
        Path path = Paths.get(keyFilePath);
        try {
            Files.write(path, key.getBytes(StandardCharsets.UTF_8));
            restrictToOwnerWindows(path);
            log.info("Key file written: {}", path.toAbsolutePath());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write key file: " + keyFilePath, ex);
        }
    }

    private static void restrictToOwnerWindows(Path path) {
        try {
            AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (aclView == null) {
                return;
            }
            List<AclEntry> acl = aclView.getAcl();
            // Keep only owner entries (ALLOW type), remove others
            java.util.Iterator<AclEntry> iter = acl.iterator();
            String ownerName = aclView.getOwner().getName();
            while (iter.hasNext()) {
                AclEntry entry = iter.next();
                if (entry.type() == AclEntryType.ALLOW && !entry.principal().getName().equals(ownerName)) {
                    iter.remove();
                }
            }
            aclView.setAcl(acl);
        } catch (Exception ex) {
            log.warn("Could not restrict key file permissions: {}", ex.getMessage());
        }
    }

    /**
     * 옵션 B: Windows Credential Manager에서 키 조회.
     * cmdkey /list:<target> 으로 확인, PowerShell Get-StoredCredential 필요.
     * 내부적으로 cmdkey를 사용해 자격증명을 읽는다.
     */
    public static String readKeyFromWindowsCredential(String credentialName) {
        if (!SAFE_CRED_NAME.matcher(credentialName).matches()) {
            throw new IllegalArgumentException("Invalid credential name (alphanumeric/underscore/hyphen only): " + credentialName);
        }
        String target = WIN_TARGET_PREFIX + credentialName;
        try {
            // PowerShell을 통해 자격증명 읽기
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe", "-NonInteractive", "-Command",
                "$c = Get-StoredCredential -Target '" + target + "' -ErrorAction Stop; $c.GetNetworkCredential().Password"
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
            proc.waitFor();
            String result = sb.toString().trim();
            if (result.isEmpty() || result.contains("not recognized") || result.contains("Error")) {
                throw new IllegalStateException("Windows Credential not found: " + target);
            }
            return result;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read Windows Credential: " + target, ex);
        }
    }

    /**
     * 옵션 B: Windows Credential Manager에 키 저장.
     * cmdkey /add 사용 — 한 번만 실행하면 됨.
     */
    public static void saveKeyToWindowsCredential(String credentialName, String key) {
        if (!SAFE_CRED_NAME.matcher(credentialName).matches()) {
            throw new IllegalArgumentException("Invalid credential name (alphanumeric/underscore/hyphen only): " + credentialName);
        }
        String target = WIN_TARGET_PREFIX + credentialName;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "cmdkey.exe",
                "/add:" + target,
                "/user:PFS_KEY_USER",
                "/pass:" + key
            );
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("{}", line);
            }
            int exit = proc.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("cmdkey failed with exit code: " + exit);
            }
            log.info("Saved to Windows Credential Manager: {}", target);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save Windows Credential: " + target, ex);
        }
    }
}

