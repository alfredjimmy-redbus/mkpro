# Operational Checklist: Zero-Downtime mTLS Rotation

This document outlines the procedure for rotating mTLS (mutual TLS) certificates and Root CAs within the `mkpro` P2P network without incurring communication downtime.

---

## 1. Pre-Rotation Validation
*Verify readiness before modifying any production nodes.*

- [ ] **Verify New CA Trust**: 
    - Distribute the **New CA Root Certificate** (`new_root_ca.crt`) to a staging node.
    - Run `keytool -printcert -file new_root_ca.crt` to verify integrity.
- [ ] **Check Expiry Dates**: 
    - Ensure the **Old CA** and current **Identity Certificates** have at least 48 hours of remaining TTL.
- [ ] **Validate New Identity Template**: 
    - Generate a test certificate from the New CA and verify extensions:
        - `Subject Alternative Name (SAN)`: Must match the node's IP/DNS.
        - `Extended Key Usage`: Must include `Client Authentication (1.3.6.1.5.5.7.3.2)` and `Server Authentication (1.3.6.1.5.5.7.3.1)`.
- [ ] **Time Synchronization**:
    - Confirm NTP/Chrony is active across all nodes. 
    - **Mitigation**: Issue new certificates with a "Not Before" date backdated by 1 hour to account for minor clock drift.

---

## 2. Rotation Sequence (Bridge Trust Method)
*Follow this exact order to maintain connectivity during transition.*

### Phase A: Expand Trust (The "Bridge")
1.  **Update Truststores**: Add the **New CA Root** to every node's `truststore.jks`. 
    - **Important**: Do NOT remove the Old CA yet.
2.  **Verify Propagation**: Confirm every node has the updated truststore.
    - Nodes at this stage: *Trust [Old CA + New CA] | Identity [Old Cert]*

### Phase B: Identity Rollout
1.  **Rolling Restart / Reload**: Replace the `identity.jks` on each node with a certificate signed by the **New CA**.
2.  **Verify Interoperability**: Because all nodes trust both CAs (from Phase A), a node on a New Cert can still talk to a node on an Old Cert.
3.  **Monitor Logs**: Watch for `SSLHandshakeException` during the rollout.

### Phase C: Cleanup
1.  **Decommission Old Trust**: Once 100% of nodes are presenting New Certs, remove the **Old CA Root** from all `truststore.jks`.
2.  **Final State**: *Trust [New CA] | Identity [New Cert]*

---

## 3. Post-Rotation Verification
*How to confirm success programmatically.*

- [ ] **Peer Metadata Check**: 
    - Update `PeerHandshake.java` to include the certificate's SHA-256 thumbprint in the `PEER_HELLO` message.
    - Check the `/network` command output to ensure all connected peers report the new thumbprint.
- [ ] **Canary Probe**:
    - Use a separate client that **only** trusts the New CA.
    - Attempt a handshake with every node. Success confirms the node is correctly presenting the new identity.
- [ ] **Log Audit**:
    - Check `P2PAuditLog` for `AUTH_FAILURE` events.
    - Ensure no "Unknown CA" errors appear in the JVM standard error stream.

---

## 4. Common Failure Modes & Mitigations

| Failure Mode | Symptom | Mitigation |
| :--- | :--- | :--- |
| **Split-Brain Trust** | Nodes A and B cannot connect; one trusts the New CA, the other does not. | Ensure Phase A (Trust Expansion) is completed and verified for the *entire* fleet before starting Phase B. |
| **Clock Skew** | `CertificateNotYetValidException` on newly issued certs. | Backdate certificate "Not Before" time by 1 hour. |
| **Partial Propagation** | Intermittent connection drops during Phase B. | This is normal if Phase A was skipped. If Phase A was done, check for mismatched SANs or KeyUsage. |
| **Keystore Locked** | Service fails to start after cert update. | Verify keystore passwords in `ConfigService` match the new JKS files before deployment. |

---

## 5. Emergency Rollback Plan
1.  **Revert Identity**: Deploy the backup of the `identity.jks` (Old Cert).
2.  **Keep Bridge Trust**: Do NOT remove the New CA from the truststore; keeping it allows you to try the rotation again without another Trust Expansion phase.
3.  **Analyze**: Check if the failure was due to SAN mismatch or incorrect KeyUsage extensions.
