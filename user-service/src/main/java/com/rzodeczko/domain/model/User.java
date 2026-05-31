package com.rzodeczko.domain.model;

import java.util.UUID;

public class User {
    private UUID id;

    private final String username;
    private final String email;

    // Jest mutable - haslo moze zostac zresetowane przez resetPassword
    private String password;

    private Role role;

    // Okresla czy user jest aktywny
    // KRYTYCZNE: boolean (primitive) NIE Boolean (wrapper).
    // Boolean wrapper może być null → NullPointerException przy isEnabled().
    // boolean primitive zawsze false lub true → bezpieczne.
    private boolean enabled;

    // MFA fields — null oznacza nieaktywne MFA.
    // mfaSecret: Base32-encoded TOTP secret używany przez auth-service do weryfikacji.
    // mfaQrUrl: URL otpauth://totp/... do wygenerowania QR kodu w Google Authenticator.
    private String mfaSecret;
    private String mfaQrUrl;

    // Konstruktor dla nowych użytkowników (rejestracja).
    // id=null — zostanie wypełnione przez Hibernate po INSERT.
    // enabled=false — konto wymaga aktywacji przez email przed pierwszym logowaniem.
    // Hasło MUSI być zaszyfrowane przed przekazaniem (Argon2) — nie weryfikujemy tu
    // bo encoder jest w infrastrukturze i domena nie ma do niego dostępu.
    public User(String username, String email, String encodedPassword, Role role) {
        this.username = username;
        this.email = email;
        this.password = encodedPassword;
        this.role = role;
        this.enabled = false;
    }

    public User(
            UUID id,
            String username,
            String email,
            String password,
            Role role,
            boolean enabled,
            String mfaSecret,
            String mfaQrUrl
    ) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
        this.mfaSecret = mfaSecret;
        this.mfaQrUrl = mfaQrUrl;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Metody domenowe
    // -----------------------------------------------------------------------------------------------------------------

    // activate() — zmiana stanu konta z nieaktywnego na aktywne.
    // Wywoływana wyłącznie po weryfikacji kodu aktywacyjnego z emaila.
    public void activate() {
        this.enabled = true;
    }

    // updatePassword() — wymiana zaszyfrowanego hasła.
    // Przyjmuje WYŁĄCZNIE zaszyfrowane hasło — szyfrowanie to odpowiedzialność
    // serwisu aplikacyjnego gdzie jest dostęp do PasswordEncoder (infrastruktura).
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    // enableMfa() — zapisanie danych MFA po setup.
    // secret: Base32 TOTP secret — auth-service użyje go do weryfikacji kodów.
    // qrUrl: URL otpauth://totp/... — frontend zakoduje jako QR kod do skanowania.
    // Oba pola ustawiane atomowo — nie można mieć secretu bez qrUrl.
    public void enableMfa(String secret, String qrUrl) {
        this.mfaSecret = secret;
        this.mfaQrUrl = qrUrl;
    }

    // hasMfaActive() — logika "czy MFA jest aktywne" należy do domeny.
    // Sprawdzamy mfaQrUrl bo to ostatnie pole ustawiane w enableMfa() —
    // gwarancja że obie wartości są ustawione.
    public boolean hasMfaActive() {
        return this.mfaQrUrl != null;
    }

    // Zmiana roli usera przez administratora.
    // Weryfikacja uprawnień (ROLE_ADMIN) odbywa się w UserServiceImpl przed wywołaniem tej metody.
    // Tutaj tylko zmiana stanu — domena nie wie o rolach requestującego.
    // Dlaczego walidacja uprawnień NIE jest tutaj?
    // Domena nie ma dostępu do kontekstu requestu (kto prosi o zmianę).
    // To odpowiedzialność application layer — serwis sprawdza uprawnienia,
    // a potem wywołuje changeRole() na domenie.
    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public String getMfaQrUrl() {
        return mfaQrUrl;
    }
}
