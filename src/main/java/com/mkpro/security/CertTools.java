package com.mkpro.security;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Interface for certificate management and SSL/TLS configuration.
 * Enhanced for zero-downtime mTLS rotation.
 */
public interface CertTools {

    /**
     * Generates a new 4096-bit RSA KeyPair.
     */
    KeyPair generateKeyPair();

    /**
     * Builds an SSLContext configured for mutual TLS (mTLS).
     */
    SSLContext buildSSLContext(InputStream p12Stream, String password) throws Exception;

    /**
     * Builds an SSLContext with multiple trust anchors (Shadow Trust).
     * 
     * @param keystoreStream Stream containing the local identity.
     * @param password Password for the keystore.
     * @param trustAnchors List of CA certificates to trust.
     * @return Initialized SSLContext.
     */
    SSLContext buildSSLContext(InputStream keystoreStream, String password, List<X509Certificate> trustAnchors) throws Exception;

    /**
     * Performs a basic check for certificate validity.
     */
    boolean isCertificateValid(X509Certificate cert, X509Certificate caCert);

    /**
     * Adds a new CA to the local trust registry.
     */
    void addTrustAnchor(X509Certificate caCert);

    /**
     * Returns the current list of trusted CA certificates.
     */
    List<X509Certificate> getTrustAnchors();

    /**
     * Removes an old CA from the trust registry.
     */
    void removeTrustAnchor(X509Certificate caCert);
}