package com.mkpro.security;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Enhanced implementation of CertTools for mTLS and certificate rotation.
 * Supports multiple trust anchors and dynamic truststore reloading.
 */
public class CertToolsImpl implements CertTools {

    private final List<X509Certificate> trustAnchors = new CopyOnWriteArrayList<>();

    @Override
    public KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(4096);
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate 4096-bit RSA key pair", e);
        }
    }

    @Override
    public SSLContext buildSSLContext(InputStream p12Stream, String password) throws Exception {
        return buildSSLContext(p12Stream, password, new ArrayList<>(trustAnchors));
    }

    @Override
    public SSLContext buildSSLContext(InputStream keystoreStream, String password, List<X509Certificate> trustAnchors) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(keystoreStream, password.toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());

        // Create a trust store that includes all provided trust anchors
        KeyStore ts = KeyStore.getInstance("PKCS12");
        ts.load(null, null);
        
        for (int i = 0; i < trustAnchors.size(); i++) {
            ts.setCertificateEntry("ca-" + i, trustAnchors.get(i));
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        
        return sslContext;
    }

    @Override
    public boolean isCertificateValid(X509Certificate cert, X509Certificate caCert) {
        try {
            cert.checkValidity(new Date());
            cert.verify(caCert.getPublicKey());
            return true;
        } catch (Exception e) {
            System.err.println("Certificate validation failed: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void addTrustAnchor(X509Certificate caCert) {
        if (!trustAnchors.contains(caCert)) {
            trustAnchors.add(caCert);
            System.out.println("[CertTools] Added new trust anchor. Total: " + trustAnchors.size());
        }
    }

    @Override
    public List<X509Certificate> getTrustAnchors() {
        return new ArrayList<>(trustAnchors);
    }

    @Override
    public void removeTrustAnchor(X509Certificate caCert) {
        if (trustAnchors.remove(caCert)) {
            System.out.println("[CertTools] Removed trust anchor. Remaining: " + trustAnchors.size());
        }
    }
}