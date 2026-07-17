# Security Policy for mTLS Operations

This document defines the security standards and operational procedures for mutual TLS (mTLS) within the `mkpro` P2P network. These policies ensure the integrity of node identities (SVIDs) and the protection of the Root Certificate Authority (CA).

---

## 1. CA Private Key Handling

The Root CA private key is the trust anchor of the entire network. Its compromise would allow an attacker to issue fraudulent identities.

### 1.1 Generation and Storage
- **Algorithm**: CA keys MUST be RSA with a minimum length of 4096 bits.
- **Generation**: CA keys MUST be generated using a cryptographically secure random number generator (CSPRNG), as implemented in `CertToolsImpl`.
- **Storage**: The CA private key MUST NOT be stored in plaintext. It should be residing in a password-protected PKCS12 keystore or a Hardware Security Module (HSM).
- **Access Control**: Only the `SignerService` component is authorized to access the CA private key for signing CSRs.

### 1.2 Rotation and Lifecycle: The Dual-Trust Method
To ensure zero-downtime during Root CA updates, `mkpro` employs a **Dual-Trust** (Expand -> Rotation -> Contraction) lifecycle.

| Phase | Name | Description | Trust State |
| :--- | :--- | :--- | :--- |
| **Phase A** | **Expansion** | Add New CA to all nodes' truststores. | Nodes trust both Old CA and New CA. |
| **Phase B** | **Rotation** | Replace node identity certs with those signed by New CA. | Handshakes succeed regardless of which CA signed the peer's cert. |
| **Phase C** | **Contraction** | Remove Old CA from truststores. | Nodes trust only the New CA. |

- **Scheduled Rotation**: The Root CA SHOULD be rotated every 12 months.
- **Decommissioning**: Old CA keys MUST be securely deleted from all nodes once Phase C is completed.

---

## 2. SVID Validation Requirements

Every node in the network identifies itself using a SPIFFE Verifiable Identity Document (SVID), which is an X.509 certificate.

### 2.1 Certificate Extensions
All SVIDs MUST include the following extensions:
- **Subject Alternative Name (SAN)**: MUST contain the node's IP address or DNS name.
- **Extended Key Usage (EKU)**: MUST include both `Client Authentication (1.3.6.1.5.5.7.3.2)` and `Server Authentication (1.3.6.1.5.5.7.3.1)`.
- **SPIFFE ID**: SHOULD be included in the SAN as a URI (e.g., `spiffe://mkpro.domain/node/<node-id>`).

### 2.2 Validation Logic
The `CertTools` implementation MUST perform the following checks on every handshake:
1.  **Signature Verification**: The certificate MUST be signed by a Root CA currently present in the node's local truststore.
2.  **Temporal Validity**: The current system time MUST fall between the `NotBefore` and `NotAfter` fields.
3.  **Whitelist Check**: The peer's IP/Identity MUST be verified against the `P2PAuditLog` whitelist.

---

## 3. Operator Manual: Monitoring & Emergency Procedures

### 3.1 Monitoring with `/cert`
The `/cert` command provides immediate visibility into a node's mTLS state.

**Usage:**
```bash
/cert
```

**Output Example:**
- **Active Thumbprint**: SHA-256 hash of the current identity certificate.
- **Expiration Date**: When the current identity expires.
- **Rotation Phase**: Current lifecycle stage (e.g., `Phase A: Expand Trust`).

### 3.2 Manual Emergency Revocation
In the event of a node compromise, follow these steps to revoke access immediately:

1.  **Identify the Target**: Use `/network peers` to find the IP/ID of the compromised node.
2.  **Remove from Whitelist**:
    - Open `~/.mkpro/p2p_whitelist.txt`.
    - Delete the entry corresponding to the compromised node.
    - Save the file. The `P2PAuditLog` watches this file and will deny future handshakes.
3.  **Force Termination**:
    - If the node is currently connected, restart your `mkpro` instance to drop the active socket.
4.  **Global Revocation (CA Compromise)**:
    - If the Root CA itself is compromised, initiate an unscheduled Dual-Trust rotation starting with **Phase A**.

### 3.3 Recovery from Stalled Rotation
If a rotation stalls (e.g., nodes lose connectivity during Phase B), use this recovery guide:

| Symptom | Cause | Recovery Step |
| :--- | :--- | :--- |
| `SSLHandshakeException: Unknown CA` | Node missed Phase A (Expansion). | Manually add the New CA to `truststore.jks` using `keytool`. |
| `CertificateExpiredException` | Rotation delayed beyond Old CA TTL. | Force an immediate Identity Rollout (Phase B) for affected nodes. |
| Node cannot connect after Phase C | Node still using Old CA Identity. | Replace `identity.jks` with a certificate signed by the New CA. |

**The "Golden Rule" of Recovery:** If the mesh is fractured, revert all nodes to **Phase A** state (Dual-Trust). This restores the "bridge" and allows nodes to communicate while you troubleshoot.

---

## 4. Audit and Alerting
- All revocation events MUST be logged in `~/.mkpro/p2p_audit.log` with the `AUTH_FAILURE` or `WHITELIST_REJECTED` event types.
- Administrators MUST review the audit logs to confirm that the revoked node is no longer attempting or succeeding in handshakes.