# Public Key Infrastructure (PKI) & Certificate Management System

##  Project Showcase Notice
>  **Academic Integrity & Privacy:** The complete source code for this project is hosted within a private GitHub organization managed by the Faculty of Technical Sciences, University of Novi Sad. To comply with university regulations and prevent academic plagiarism, the source code is kept confidential. This repository serves as a **comprehensive technical specification, architectural blueprint, and portfolio showcase** detailing my specific contributions and implementation choices.

---

##  System Architecture & Core Features

This is an enterprise-grade, full-stack application designed to model a secure, hierarchical Certificate Authority (CA) system. It manages the entire lifecycle of digital certificates while ensuring strict compliance with cryptographic and web security standards.

###  1. Cryptographic Lifecycle Management
* **Hierarchical Trust Chain:** Designed and developed a system capable of issuing, verifying, and managing **Root CA**, **Intermediate CA**, and **End-Entity X.509 certificates**.
* **Revocation Infrastructure:** Implemented complete **Certificate Revocation List (CRL)** support, allowing instantaneous checks for compromised or expired certificates.

###  2. Advanced Security Architecture
* **Client-Side Decryption:** Utilized the native browser **WebCryptoAPI** to shift critical decryption processes to the client side, ensuring sensitive data minimization.
* **Key Lifecycle Management:** Engineered a resilient **Master Key Rotation** mechanism to periodically update and protect system-wide root cryptographic keys.
* **Access Control:** Integrated Multi-Factor Authentication (**2FA**) for critical administrative actions, such as certificate signing and revocation requests.

###  3. Secure Infrastructure & Stateless Sessions
* **Transport Security:** Enforced full **HTTPS/TLS** across all inter-service communications to prevent Man-in-the-Middle (MitM) attacks.
* **Authentication Pipeline:** Architected a secure, stateless authentication mechanism utilizing **JWT (Access and Refresh token pattern)**, complete with secure token handling and expiration logic.

---
## Snapshots
### Login page
<img width="766" height="581" alt="login" src="https://github.com/user-attachments/assets/8f00a0f9-acdb-4924-a823-30d40a77e51e" />

### Registration with email activation using generated private key

<img width="640" height="778" alt="register" src="https://github.com/user-attachments/assets/686ed2f9-cbdb-4455-b89c-dff356f430fe" />
<img width="642" height="745" alt="register2" src="https://github.com/user-attachments/assets/af188039-fcad-440f-922d-9688a689e6e0" />

### Issuing certificate

<img width="641" height="799" alt="issueCert" src="https://github.com/user-attachments/assets/dddc351f-0556-413d-bf82-2d7613505486" />

### Certificate overview, revocation, download...

<img width="2522" height="698" alt="certificatesOverview" src="https://github.com/user-attachments/assets/28cedba9-2451-48e1-8d38-00a42be5a2c6" />
<img width="573" height="335" alt="download1nopriv" src="https://github.com/user-attachments/assets/bd7e7239-5627-4249-8a72-0943a970d1a2" />
<img width="572" height="416" alt="downloadwithprivate" src="https://github.com/user-attachments/assets/75a3282e-f9a9-488c-85cb-849ddedc1351" />
<img width="1082" height="804" alt="certificate" src="https://github.com/user-attachments/assets/8301a3f0-593d-4e13-a40c-65b5b48d5697" />
<img width="582" height="440" alt="revoke" src="https://github.com/user-attachments/assets/16a3fe3d-a64d-4732-97bd-27fe48f3198a" />




---

##  Tech Stack & Tools

* **Backend:** Java 17, Spring Boot, Spring Security, Hibernate/JPA
* **Cryptographic Providers:** Java Cryptography Architecture (JCA) / Bouncy Castle
* **Frontend:** TypeScript, Angular, RxJS, WebCryptoAPI
* **Database:** PostgreSQL
* **Methodology & Tooling:** Agile/Scrum, Git for version control

---

##  Key Takeaways & Engineering Impact
Building this system provided deep hands-on experience in balancing user experience with zero-trust security principles. It required a rigorous understanding of the X.509 standard, secure session lifecycle design, and the practical implementation of cryptographic primitives in a modern web environment.
