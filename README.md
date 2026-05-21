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

##  Tech Stack & Tools

* **Backend:** Java 17, Spring Boot, Spring Security, Hibernate/JPA
* **Cryptographic Providers:** Java Cryptography Architecture (JCA) / Bouncy Castle
* **Frontend:** TypeScript, Angular, RxJS, WebCryptoAPI
* **Database:** PostgreSQL
* **Methodology & Tooling:** Agile/Scrum, Git for version control

---

##  Key Takeaways & Engineering Impact
Building this system provided deep hands-on experience in balancing user experience with zero-trust security principles. It required a rigorous understanding of the X.509 standard, secure session lifecycle design, and the practical implementation of cryptographic primitives in a modern web environment.
