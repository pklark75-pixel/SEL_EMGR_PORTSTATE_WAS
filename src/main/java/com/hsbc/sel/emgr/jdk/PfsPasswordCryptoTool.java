package com.hsbc.sel.emgr.jdk;

import com.hsbc.sel.emgr.security.AesCryptoUtil;
import com.hsbc.sel.emgr.security.KeyStoreUtil;

public final class PfsPasswordCryptoTool {

    private PfsPasswordCryptoTool() {
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            printHelp();
            return;
        }

        String mode = args[0];

        // ── 환경변수 키 방식 ──────────────────────────
        if ("encrypt".equalsIgnoreCase(mode)) {
            String key = requireEnvKey();
            System.out.println("db.password.enc=" + AesCryptoUtil.encrypt(args[1], key));
            return;
        }

        if ("decrypt".equalsIgnoreCase(mode)) {
            String key = requireEnvKey();
            System.out.println("plain=" + AesCryptoUtil.decrypt(args[1], key));
            return;
        }

        // ── 옵션 A: 키 파일 방식 ──────────────────────
        if ("write-key-file".equalsIgnoreCase(mode)) {
            // args: write-key-file <keyFilePath> <keyValue>
            if (args.length < 3) {
                System.out.println("Usage: write-key-file <keyFilePath> <keyValue>");
                return;
            }
            KeyStoreUtil.writeKeyFile(args[1], args[2]);
            System.out.println("Key saved. Add to pfs-jdk.properties:");
            System.out.println("db.password.keyFile=" + args[1]);
            return;
        }

        if ("encrypt-with-file".equalsIgnoreCase(mode)) {
            // args: encrypt-with-file <keyFilePath> <plainPassword>
            if (args.length < 3) {
                System.out.println("Usage: encrypt-with-file <keyFilePath> <plainPassword>");
                return;
            }
            String key = KeyStoreUtil.readKeyFromFile(args[1]);
            System.out.println("db.password.enc=" + AesCryptoUtil.encrypt(args[2], key));
            return;
        }

        if ("decrypt-with-file".equalsIgnoreCase(mode)) {
            // args: decrypt-with-file <keyFilePath> <encBase64>
            if (args.length < 3) {
                System.out.println("Usage: decrypt-with-file <keyFilePath> <encBase64>");
                return;
            }
            String key = KeyStoreUtil.readKeyFromFile(args[1]);
            System.out.println("plain=" + AesCryptoUtil.decrypt(args[2], key));
            return;
        }

        // ── 옵션 B: Windows Credential Manager 방식 ──
        if ("save-wincred".equalsIgnoreCase(mode)) {
            // args: save-wincred <credentialName> <keyValue>
            if (args.length < 3) {
                System.out.println("Usage: save-wincred <credentialName> <keyValue>");
                return;
            }
            KeyStoreUtil.saveKeyToWindowsCredential(args[1], args[2]);
            System.out.println("Add to pfs-jdk.properties:");
            System.out.println("db.password.keyWinCred=" + args[1]);
            return;
        }

        if ("encrypt-with-wincred".equalsIgnoreCase(mode)) {
            // args: encrypt-with-wincred <credentialName> <plainPassword>
            if (args.length < 3) {
                System.out.println("Usage: encrypt-with-wincred <credentialName> <plainPassword>");
                return;
            }
            String key = KeyStoreUtil.readKeyFromWindowsCredential(args[1]);
            System.out.println("db.password.enc=" + AesCryptoUtil.encrypt(args[2], key));
            return;
        }

        System.out.println("Unknown mode: " + mode);
        printHelp();
    }

    private static String requireEnvKey() {
        String key = System.getenv("PFS_DB_KEY");
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalStateException("Set environment variable PFS_DB_KEY first.");
        }
        return key.trim();
    }

    private static void printHelp() {
        System.out.println("=== PFS Password Crypto Tool ===");
        System.out.println();
        System.out.println("[옵션1 - 환경변수 키] env PFS_DB_KEY 필요");
        System.out.println("  encrypt <plainPassword>        암호화 → db.password.enc=...");
        System.out.println("  decrypt <encBase64>            복호화 확인");
        System.out.println();
        System.out.println("[옵션2 - 키 파일]");
        System.out.println("  write-key-file <file> <key>       키 파일 생성 (소유자 권한 제한)");
        System.out.println("  encrypt-with-file <file> <pw>     키 파일로 암호 암호화");
        System.out.println("  decrypt-with-file <file> <enc>    키 파일로 복호화 확인");
        System.out.println();
        System.out.println("[옵션3 - Windows Credential Manager]");
        System.out.println("  save-wincred <name> <key>      Windows 자격증명에 키 저장");
        System.out.println("  encrypt-with-wincred <name> <pw> Windows 자격증명 키로 암호화");
    }
}
