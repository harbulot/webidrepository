/*

Copyright (c) 2008-2010, The University of Manchester, United Kingdom.
All rights reserved.

Redistribution and use in source and binary forms, with or without 
modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, 
      this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright 
      notice, this list of conditions and the following disclaimer in the 
      documentation and/or other materials provided with the distribution.
 * Neither the name of The University of Manchester nor the names of 
      its contributors may be used to endorse or promote products derived 
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
POSSIBILITY OF SUCH DAMAGE.

  Author........: Bruno Harbulot
 
 */
package uk.ac.manchester.rcs.bruno.keygenapp.base;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.asn1.misc.MiscObjectIdentifiers;
import org.bouncycastle.asn1.misc.NetscapeCertType;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.PKCS10CertificationRequest;
import org.bouncycastle.jce.netscape.NetscapeCertRequest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.bouncycastle.x509.extension.SubjectKeyIdentifierStructure;

/**
 * This class provides the mechanisms to emit certificates for a mini CA.
 * 
 * @author Bruno Harbulot.
 * 
 */
public class MiniCaCertGen {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private MiniCaCertGen() {

    }

    private final static Logger LOGGER = Logger.getLogger(MiniCaCertGen.class
            .getName());

    /**
     * Creates a certificate, containing a subjectAltName URI.
     * 
     * @param caPubKey
     *            CA public key
     * @param caPrivKey
     *            CA private key
     * @param certPubKey
     *            certificate public key
     * @param subject
     *            subject (and issuer) DN for this certificate, RFC 2253 format
     *            preferred.
     * @param startDate
     *            date from and until which the certificate will be valid
     *            (defaults to current date and time if null)
     * @param endDate
     *            date until which the certificate will be valid (defaults to
     *            365 days after start date if null)
     * @param subjAltNameURI
     *            URI to be placed in subjectAltName
     * @param serialNumber
     *            certificate serial number
     * @return certificate
     * @throws InvalidKeyException
     * @throws SignatureException
     * @throws NoSuchAlgorithmException
     * @throws IllegalStateException
     * @throws NoSuchProviderException
     * @throws CertificateException
     * @throws Exception
     */
    public static X509Certificate createCert(PublicKey caPubKey,
            PrivateKey caPrivKey, PublicKey certPubKey, X509Name subject,
            X509Name issuer, Date startDate, Date endDate,
            String subjAltNameURI, BigInteger serialNumber)
            throws InvalidKeyException, IllegalStateException,
            NoSuchAlgorithmException, SignatureException, CertificateException,
            NoSuchProviderException {

        X509V3CertificateGenerator certGenerator = new X509V3CertificateGenerator();

        certGenerator.reset();
        /*
         * Sets up the subject distinguished name. Since it's a self-signed
         * certificate, issuer and subject are the same.
         */
        certGenerator.setIssuerDN(issuer);
        certGenerator.setSubjectDN(subject);

        /*
         * Sets up the validity dates.
         */
        if (startDate == null) {
            startDate = new Date(System.currentTimeMillis());
        }
        certGenerator.setNotBefore(startDate);
        if (endDate == null) {
            endDate = new Date(startDate.getTime() + 365L * 24L * 60L * 60L
                    * 1000L);
        }
        certGenerator.setNotAfter(endDate);

        /*
         * Setting the serial-number of this certificate.
         */
        certGenerator.setSerialNumber(serialNumber);
        /*
         * Sets the public-key to embed in this certificate.
         */
        certGenerator.setPublicKey(certPubKey);
        /*
         * Sets the signature algorithm.
         */
        String pubKeyAlgorithm = caPubKey.getAlgorithm();
        if (pubKeyAlgorithm.equals("DSA")) {
            certGenerator.setSignatureAlgorithm("SHA1WithDSA");
        } else if (pubKeyAlgorithm.equals("RSA")) {
            certGenerator.setSignatureAlgorithm("SHA1WithRSAEncryption");
        } else {
            RuntimeException re = new RuntimeException(
                    "Algorithm not recognised: " + pubKeyAlgorithm);
            LOGGER.log(Level.SEVERE, re.getMessage(), re);
            throw re;
        }

        /*
         * Adds the Basic Constraint (CA: false) extension.
         */
        certGenerator.addExtension(X509Extensions.BasicConstraints, true,
                new BasicConstraints(false));

        /*
         * Adds the Key Usage extension.
         */
        certGenerator.addExtension(X509Extensions.KeyUsage, true, new KeyUsage(
                KeyUsage.digitalSignature | KeyUsage.nonRepudiation
                        | KeyUsage.keyEncipherment | KeyUsage.keyAgreement
                        | KeyUsage.keyCertSign));

        /*
         * Adds the Netscape certificate type extension.
         */
        certGenerator.addExtension(MiscObjectIdentifiers.netscapeCertType,
                false, new NetscapeCertType(NetscapeCertType.sslClient
                        | NetscapeCertType.smime));

        /*
         * Adds the subject key identifier extension.
         */
        SubjectKeyIdentifier subjectKeyIdentifier = new SubjectKeyIdentifierStructure(
                certPubKey);
        certGenerator.addExtension(X509Extensions.SubjectKeyIdentifier, false,
                subjectKeyIdentifier);

        /*
         * Adds the subject alternative-name extension (critical).
         */
        if (subjAltNameURI != null) {
            GeneralNames subjectAltNames = new GeneralNames(new GeneralName(
                    GeneralName.uniformResourceIdentifier, subjAltNameURI));
            certGenerator.addExtension(X509Extensions.SubjectAlternativeName,
                    true, subjectAltNames);
        }

        /*
         * Creates and sign this certificate with the private key corresponding
         * to the public key of the certificate (hence the name
         * "self-signed certificate").
         */
        X509Certificate cert = certGenerator.generate(caPrivKey);

        /*
         * Checks that this certificate has indeed been correctly signed.
         */
        cert.verify(caPubKey);

        return cert;
    }

    /**
     * Creates a certificate, containing a subjectAltName URI.
     * 
     * @param caPubKey
     *            CA public key
     * @param caPrivKey
     *            CA private key
     * @param netscapeCertReq
     *            NetscapeCertRequest object build from the SPKAC data
     * @param subject
     *            subject (and issuer) DN for this certificate, RFC 2253 format
     *            preferred.
     * @param startDate
     *            date from and until which the certificate will be valid
     *            (defaults to current date and time if null)
     * @param endDate
     *            date until which the certificate will be valid (defaults to
     *            365 days after start date if null)
     * @param subjAltNameURI
     *            URI to be placed in subjectAltName
     * @param serialNumber
     *            certificate serial number
     * @return certificate
     * @throws IOException
     * @throws InvalidKeyException
     * @throws IllegalStateException
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws CertificateException
     * @throws NoSuchProviderException
     */
    public static X509Certificate createCert(PublicKey caPubKey,
            PrivateKey caPrivKey, NetscapeCertRequest netscapeCertReq,
            X509Name subject, X509Name issuer, Date startDate, Date endDate,
            String subjAltNameURI, BigInteger serialNumber) throws IOException,
            InvalidKeyException, IllegalStateException,
            NoSuchAlgorithmException, SignatureException, CertificateException,
            NoSuchProviderException {
        return createCert(caPubKey, caPrivKey, netscapeCertReq.getPublicKey(),
                subject, issuer, startDate, endDate, subjAltNameURI,
                serialNumber);
    }

    /**
     * Creates a certificate, containing a subjectAltName URI.
     * 
     * @param caPubKey
     *            CA public key
     * @param caPrivKey
     *            CA private key
     * @param pkcs10csr
     *            PKCS10CertificationRequest object (representing the
     *            certification request)
     * @param subject
     *            subject (and issuer) DN for this certificate, RFC 2253 format
     *            preferred.
     * @param startDate
     *            date from and until which the certificate will be valid
     *            (defaults to current date and time if null)
     * @param endDate
     *            date until which the certificate will be valid (defaults to
     *            365 days after start date if null)
     * @param subjAltNameURI
     *            URI to be placed in subjectAltName
     * @param serialNumber
     *            certificate serial number
     * @return certificate
     * @throws IOException
     * @throws InvalidKeyException
     * @throws IllegalStateException
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws CertificateException
     * @throws NoSuchProviderException
     */
    public static X509Certificate createCert(PublicKey caPubKey,
            PrivateKey caPrivKey, PKCS10CertificationRequest pkcs10csr,
            X509Name subject, X509Name issuer, Date startDate, Date endDate,
            String subjAltNameURI, BigInteger serialNumber) throws IOException,
            InvalidKeyException, IllegalStateException,
            NoSuchAlgorithmException, SignatureException, CertificateException,
            NoSuchProviderException {
        return createCert(caPubKey, caPrivKey, pkcs10csr.getPublicKey(),
                subject, issuer, startDate, endDate, subjAltNameURI,
                serialNumber);
    }

    /**
     * Creates a certificate, containing a subjectAltName URI.
     * 
     * @param caPubKey
     *            CA public key
     * @param caPrivKey
     *            CA private key
     * @param spkacData
     *            SPKAC data obtained from the KEYGEN tag
     * @param subject
     *            subject (and issuer) DN for this certificate, RFC 2253 format
     *            preferred.
     * @param startDate
     *            date from and until which the certificate will be valid
     *            (defaults to current date and time if null)
     * @param endDate
     *            date until which the certificate will be valid (defaults to
     *            365 days after start date if null)
     * @param subjAltNameURI
     *            URI to be placed in subjectAltName
     * @param serialNumber
     *            certificate serial number
     * @return certificate
     * @throws IOException
     * @throws InvalidKeyException
     * @throws IllegalStateException
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws CertificateException
     * @throws NoSuchProviderException
     */
    public static X509Certificate createCertFromSpkac(PublicKey caPubKey,
            PrivateKey caPrivKey, String spkacData, X509Name subject,
            X509Name issuer, Date startDate, Date endDate,
            String subjAltNameURI, BigInteger serialNumber) throws IOException,
            InvalidKeyException, IllegalStateException,
            NoSuchAlgorithmException, SignatureException, CertificateException,
            NoSuchProviderException {
        return createCert(caPubKey, caPrivKey, new NetscapeCertRequest(Base64
                .decode(spkacData)), subject, issuer, startDate, endDate,
                subjAltNameURI, serialNumber);
    }

    /**
     * Creates a certificate, containing a subjectAltName URI.
     * 
     * @param caPubKey
     *            CA public key
     * @param caPrivKey
     *            CA private key
     * @param spkacData
     *            SPKAC data obtained from the KEYGEN tag
     * @param subject
     *            subject (and issuer) DN for this certificate, RFC 2253 format
     *            preferred.
     * @param startDate
     *            date from and until which the certificate will be valid
     *            (defaults to current date and time if null)
     * @param endDate
     *            date until which the certificate will be valid (defaults to
     *            365 days after start date if null)
     * @param subjAltNameURI
     *            URI to be placed in subjectAltName
     * @param serialNumber
     *            certificate serial number
     * @return certificate
     * @throws IOException
     * @throws InvalidKeyException
     * @throws IllegalStateException
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws CertificateException
     * @throws NoSuchProviderException
     */
    public static X509Certificate createCertFromPemCsr(PublicKey caPubKey,
            PrivateKey caPrivKey, String pemCsr, X509Name subject,
            X509Name issuer, Date startDate, Date endDate,
            String subjAltNameURI, BigInteger serialNumber) throws IOException,
            InvalidKeyException, IllegalStateException,
            NoSuchAlgorithmException, SignatureException, CertificateException,
            NoSuchProviderException {

        PEMReader pemReader = new PEMReader(new StringReader(pemCsr));
        Object pemObject = pemReader.readObject();
        if (pemObject instanceof PKCS10CertificationRequest) {
            PKCS10CertificationRequest pkcs10Obj = (PKCS10CertificationRequest) pemObject;

            return createCert(caPubKey, caPrivKey, pkcs10Obj, subject, issuer,
                    startDate, endDate, subjAltNameURI, serialNumber);
        } else {
            throw new IOException("Unable to read PEM CSR data.");
        }
    }
}
