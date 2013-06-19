/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * Date: 11/01/11
 * You may contact the copyright holder at: soporte.afirma5@mpt.es
 */

package es.gob.afirma.signers.cadestri.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Properties;
import java.util.logging.Logger;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.AOInvalidFormatException;
import es.gob.afirma.core.misc.Base64;
import es.gob.afirma.core.misc.UrlHttpManagerImpl;
import es.gob.afirma.core.signers.AOPkcs1Signer;
import es.gob.afirma.core.signers.AOSignInfo;
import es.gob.afirma.core.signers.AOSigner;
import es.gob.afirma.core.signers.CounterSignTarget;
import es.gob.afirma.core.util.tree.AOTreeModel;

/** Firmador CAdES en tres fases.
 * @author Tom&acute;s Garc&iacute;a-Mer&aacute;s */
public final class AOCAdESTriPhaseSigner implements AOSigner {

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	/** Nombre de la propiedad de URL del servidor de firma trif&aacute;sica. */
	private static final String PROPERTY_NAME_SIGN_SERVER_URL = "serverUrl"; //$NON-NLS-1$

	/** Identificador de la operacion de prefirma en servidor. */
	private static final String OPERATION_PRESIGN = "pre"; //$NON-NLS-1$

	/** Identificador de la operacion de postfirma en servidor. */
	private static final String OPERATION_POSTSIGN = "post"; //$NON-NLS-1$

	/** Identificador de la operaci&oacute;n criptogr&aacute;fica de firma. */
	private static final String CRYPTO_OPERATION_SIGN = "sign"; //$NON-NLS-1$

	/** Identificador de la operaci&oacute;n criptogr&aacute;fica de cofirma. */
	private static final String CRYPTO_OPERATION_COSIGN = "cosign"; //$NON-NLS-1$

	/** Identificador de la operaci&oacute;n criptogr&aacute;fica de contrafirma. */
	private static final String CRYPTO_OPERATION_COUNTERSIGN = "countersign"; //$NON-NLS-1$
	
	/** Nombre del par&aacute;metro de c&oacute;digo de operaci&oacute;n en la URL de llamada al servidor de firma. */
	private static final String PARAMETER_NAME_OPERATION = "op"; //$NON-NLS-1$

	/** Nombre del par&aacute;metro que identifica la operaci&oacute;n criptogr&aacute;fica en la URL del servidor de firma. */
	private static final String PARAMETER_NAME_CRYPTO_OPERATION = "cop"; //$NON-NLS-1$

	private static final String HTTP_CGI = "?"; //$NON-NLS-1$
	private static final String HTTP_EQUALS = "="; //$NON-NLS-1$
	private static final String HTTP_AND = "&"; //$NON-NLS-1$

	// Parametros que necesitamos para la URL de las llamadas al servidor de firma
	private static final String PARAMETER_NAME_DOCID = "doc"; //$NON-NLS-1$
	private static final String PARAMETER_NAME_ALGORITHM = "algo"; //$NON-NLS-1$
	private static final String PARAMETER_NAME_FORMAT = "format"; //$NON-NLS-1$
	private static final String PARAMETER_NAME_CERT = "cert"; //$NON-NLS-1$
	private static final String PARAMETER_NAME_EXTRA_PARAM = "params"; //$NON-NLS-1$

	private static final String CADES_FORMAT = "CAdES"; //$NON-NLS-1$

	// Nombres de las propiedades intercambiadas con el servidor como Properties

	/** Prefijo para las propiedades que almacenan prefirmas. */
	private static final String PROPERTY_NAME_PRESIGN_PREFIX = "PRE."; //$NON-NLS-1$

	/** Nombre de la propiedad de firma. Utilizada solo cuando es necesario mantener una estructura
	 * de firma sobre la que trabajar, comun a todas las firmas realizadas (como en la contrafirma). */
	private static final String PROPERTY_NAME_SIGN = "SIGN"; //$NON-NLS-1$
	
	/** Nombre de la propiedad que contiene el n&uacute;mero de firmas proporcionadas. */
	private static final String PROPERTY_NAME_SIGN_COUNT = "SIGN.COUNT"; //$NON-NLS-1$
	
	/** Nombre de la propiedad de los sesi&oacute;n necesarios para completar la firma. */
	private static final String PROPERTY_NAME_SESSION_DATA_PREFIX = "SESSION."; //$NON-NLS-1$

	/** Firma PKCS#1. */
	private static final String PROPERTY_NAME_PKCS1_SIGN_PREFIX = "PK1."; //$NON-NLS-1$
	
	/** Nombre de la propiedad con los nodos objetivo para la contrafirma. */
	private static final String PROPERTY_NAME_CS_TARGET = "target"; //$NON-NLS-1$


	/** Indicador de finalizaci&oacute;n correcta de proceso. */
	private static final String SUCCESS = "OK"; //$NON-NLS-1$


	@Override
	public byte[] sign(final byte[] data,
			final String algorithm,
			final PrivateKey key,
			final Certificate[] certChain,
			final Properties extraParams) throws AOException, IOException {
		return triPhaseOperation(CRYPTO_OPERATION_SIGN, data, algorithm, key, certChain, extraParams);
	}

	@Override
	public byte[] cosign(final byte[] data,
			final byte[] sign,
			final String algorithm,
			final PrivateKey key,
			final Certificate[] certChain,
			final Properties extraParams) throws AOException, IOException {
		return cosign(sign, algorithm, key, certChain, extraParams);
	}

	@Override
	public byte[] cosign(final byte[] sign,
			final String algorithm,
			final PrivateKey key,
			final Certificate[] certChain,
			final Properties extraParams)
					throws AOException, IOException {
		return triPhaseOperation(CRYPTO_OPERATION_COSIGN, sign, algorithm, key, certChain, extraParams);
	}

	@Override
	public byte[] countersign(final byte[] sign,
			final String algorithm,
			final CounterSignTarget targetType,
			final Object[] targets,
			final PrivateKey key,
			final Certificate[] certChain,
			final Properties extraParams) throws AOException, IOException {
		
		// Si no se ha definido nodos objeto de la contrafirma se definen los nodos hijo
		if (targetType == null) {
			throw new IllegalArgumentException("No se han indicado los nodos objetivo de la contrafirma"); //$NON-NLS-1$
		}
		
		// Comprobamos si es un tipo de contrafirma soportado
    	if (targetType != CounterSignTarget.TREE && targetType != CounterSignTarget.LEAFS) {
    		throw new UnsupportedOperationException("El objetivo indicado para la contrafirma no esta soportado: " + targetType); //$NON-NLS-1$
    	}
		
		extraParams.setProperty(PROPERTY_NAME_CS_TARGET, targetType.toString());
		
		return triPhaseOperation(CRYPTO_OPERATION_COUNTERSIGN, sign, algorithm, key, certChain, extraParams);
	}

	@Override
	public AOTreeModel getSignersStructure(final byte[] sign,
			final boolean asSimpleSignInfo) throws AOInvalidFormatException, IOException {
		throw new UnsupportedOperationException("No se soporta en firma trifasica"); //$NON-NLS-1$
	}

	@Override
	public boolean isSign(final byte[] is) throws IOException {
		throw new UnsupportedOperationException("No se soporta en firma trifasica"); //$NON-NLS-1$
	}

	@Override
	public boolean isValidDataFile(final byte[] data) throws IOException {
		if (data == null) {
			LOGGER.warning("Se han introducido datos nulos para su comprobacion"); //$NON-NLS-1$
			return false;
		}
		return true;
	}

	@Override
	public String getSignedName(final String originalName, final String inText) {
		return originalName + (inText != null ? inText : "") + ".csig"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public byte[] getData(final byte[] signData) throws AOException, IOException {
		throw new UnsupportedOperationException("No se soporta en firma trifasica"); //$NON-NLS-1$
	}

	@Override
	public AOSignInfo getSignInfo(final byte[] signData) throws AOException, IOException {
		throw new UnsupportedOperationException("No se soporta en firma trifasica"); //$NON-NLS-1$
	}

	private static String properties2Base64(final Properties p) throws IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		p.store(baos, ""); //$NON-NLS-1$
		return Base64.encodeBytes(baos.toByteArray(), Base64.URL_SAFE);
	}

	private static Properties base642Properties(final String base64) throws IOException {
		final Properties p = new Properties();
		p.load(new ByteArrayInputStream(Base64.decode(base64, Base64.URL_SAFE)));
		return p;
	}

	private static byte[] triPhaseOperation(final String cryptoOperation,
			final byte[] data,
			final String algorithm,
			final PrivateKey key,
			final Certificate[] certChain,
			final Properties extraParams) throws AOException {

		if (extraParams == null) {
			throw new IllegalArgumentException("Se necesitan parametros adicionales"); //$NON-NLS-1$
		}
		if (key == null) {
			throw new IllegalArgumentException("Es necesario proporcionar una clave privada de firma"); //$NON-NLS-1$
		}
		if (certChain == null || certChain.length == 0) {
			throw new IllegalArgumentException("Es necesario proporcionar un certificado de firma"); //$NON-NLS-1$
		}
		if (data == null) {
			throw new IllegalArgumentException("No se ha proporcionado el identificador de documento a firmar"); //$NON-NLS-1$
		}

		// Creamos una copia de los parametros
		final Properties configParams = new Properties();
		for (final String keyParam : extraParams.keySet().toArray(new String[extraParams.size()])) {
			configParams.setProperty(keyParam, extraParams.getProperty(keyParam));
		}

		// Comprobamos la direccion del servidor
		final URL signServerUrl;
		try {
			signServerUrl = new URL(configParams.getProperty(PROPERTY_NAME_SIGN_SERVER_URL));
		}
		catch (final MalformedURLException e) {
			throw new IllegalArgumentException("No se ha proporcionado la URL del servidor de firma: " + configParams.getProperty(PROPERTY_NAME_SIGN_SERVER_URL), e); //$NON-NLS-1$
		}
		configParams.remove(PROPERTY_NAME_SIGN_SERVER_URL);

		// Decodificamos el identificador del documento
		final String documentId;
		try {
			documentId = Base64.encodeBytes(data, Base64.URL_SAFE);
		} catch (final IOException e) {
			throw new IllegalArgumentException("Error al interpretar los datos como identificador del documento que desea firmar", e); //$NON-NLS-1$
		}

		// ---------
		// PREFIRMA
		// ---------

		// Empezamos la prefirma
		final byte[] preSignResult;
		try {
			// Llamamos a una URL pasando como parametros los datos necesarios para
			// configurar la operacion:
			//  - Operacion trifasica (prefirma o postfirma)
			//  - Formato de firma
			//  - Algoritmo de firma a utilizar
			//  - Certificado de firma
			//  - Parametros extra de configuracion
			//  - Datos o identificador del documento a firmar
			final StringBuffer urlBuffer = new StringBuffer();
			urlBuffer.append(signServerUrl).append(HTTP_CGI).
			append(PARAMETER_NAME_OPERATION).append(HTTP_EQUALS).append(OPERATION_PRESIGN).append(HTTP_AND).
			append(PARAMETER_NAME_CRYPTO_OPERATION).append(HTTP_EQUALS).append(cryptoOperation).append(HTTP_AND).
			append(PARAMETER_NAME_FORMAT).append(HTTP_EQUALS).append(CADES_FORMAT).append(HTTP_AND).
			append(PARAMETER_NAME_ALGORITHM).append(HTTP_EQUALS).append(algorithm).append(HTTP_AND).
			append(PARAMETER_NAME_CERT).append(HTTP_EQUALS).append(Base64.encodeBytes(certChain[0].getEncoded(), Base64.URL_SAFE));

			if (configParams.size() > 0) {
				urlBuffer.append(HTTP_AND).append(PARAMETER_NAME_EXTRA_PARAM).append(HTTP_EQUALS).
				append(properties2Base64(configParams));
			}

			urlBuffer.append(HTTP_AND).append(PARAMETER_NAME_DOCID).append(HTTP_EQUALS).
			append(documentId);

			preSignResult = UrlHttpManagerImpl.readUrlByPost(urlBuffer.toString());
			urlBuffer.setLength(0);

		}
		catch (final CertificateEncodingException e) {
			throw new AOException("Error decodificando el certificado del firmante: " + e, e); //$NON-NLS-1$
		}
		catch (final IOException e) {
			throw new AOException("Error en la llamada de prefirma al servidor: " + e, e); //$NON-NLS-1$
		}

		// Convertimos la respuesta del servidor en un Properties
		final Properties preSign;
		try {
			preSign = base642Properties(new String(preSignResult));
		}
		catch (final IOException e) {
			throw new AOException("La respuesta del servidor no es valida: " + new String(preSignResult), e); //$NON-NLS-1$
		}
		
		// ----------
		// FIRMA
		// ----------

		// Es posible que se ejecute mas de una firma como resultado de haber proporcionado varios
		// identificadores de datos o en una operacion de postfirma.
		
		if (!preSign.containsKey(PROPERTY_NAME_SIGN_COUNT)) {
			throw new AOException("El servidor no ha informado del numero de firmas que envia"); //$NON-NLS-1$
		}
		
		final int signCount = Integer.parseInt(preSign.getProperty(PROPERTY_NAME_SIGN_COUNT));
		for (int i = 0; i < signCount; i++) {
			final String base64PreSign = preSign.getProperty(PROPERTY_NAME_PRESIGN_PREFIX + i);
			if (base64PreSign == null) {
				throw new AOException("El servidor no ha devuelto la prefirma numero " + i + ": " + new String(preSignResult)); //$NON-NLS-1$ //$NON-NLS-2$
			}
		
			final byte[] cadesSignedAttributes;
			try {
				cadesSignedAttributes = Base64.decode(base64PreSign);
			}
			catch (final IOException e) {
				throw new AOException("Error decodificando los atributos CAdES a firmar: " + e, e); //$NON-NLS-1$
			}
			
			final byte[] pkcs1sign = new AOPkcs1Signer().sign(
					cadesSignedAttributes,
					algorithm,
					key,
					certChain,
					null // No hay parametros en PKCS#1
					);
			
			// Configuramos la peticion de postfirma indicando las firmas PKCS#1 generadas
			configParams.setProperty(PROPERTY_NAME_PKCS1_SIGN_PREFIX + i, Base64.encode(pkcs1sign));
			// Si hay datos de sesion, los indicamos tambien
			if (preSign.containsKey(PROPERTY_NAME_SESSION_DATA_PREFIX + i)) {
				configParams.setProperty(PROPERTY_NAME_SESSION_DATA_PREFIX + i, preSign.getProperty(PROPERTY_NAME_SESSION_DATA_PREFIX + i));
			}
		}
		
		// Agregamos la configuracion general
		configParams.setProperty(PROPERTY_NAME_SIGN_COUNT, preSign.getProperty(PROPERTY_NAME_SIGN_COUNT));
		if (preSign.containsKey(PROPERTY_NAME_SIGN)) {
			configParams.setProperty(PROPERTY_NAME_SIGN, preSign.getProperty(PROPERTY_NAME_SIGN));
		}
		
		// ---------
		// POSTFIRMA
		// ---------
		
		final byte[] triSignFinalResult;
		try {
			final StringBuffer urlBuffer = new StringBuffer();
			urlBuffer.append(signServerUrl).append(HTTP_CGI).
			append(PARAMETER_NAME_OPERATION).append(HTTP_EQUALS).append(OPERATION_POSTSIGN).append(HTTP_AND).
			append(PARAMETER_NAME_CRYPTO_OPERATION).append(HTTP_EQUALS).append(cryptoOperation).append(HTTP_AND).
			append(PARAMETER_NAME_FORMAT).append(HTTP_EQUALS).append(CADES_FORMAT).append(HTTP_AND).
			append(PARAMETER_NAME_ALGORITHM).append(HTTP_EQUALS).append(algorithm).append(HTTP_AND).
			append(PARAMETER_NAME_CERT).append(HTTP_EQUALS).append(Base64.encodeBytes(certChain[0].getEncoded(), Base64.URL_SAFE)).
			append(HTTP_AND).append(PARAMETER_NAME_EXTRA_PARAM).append(HTTP_EQUALS).append(properties2Base64(configParams));

			urlBuffer.append(HTTP_AND).append(PARAMETER_NAME_DOCID).append(HTTP_EQUALS).
			append(documentId);

			triSignFinalResult = UrlHttpManagerImpl.readUrlByPost(urlBuffer.toString());
			urlBuffer.setLength(0);
		}
		catch (final CertificateEncodingException e) {
			throw new AOException("Error decodificando el certificado del firmante: " + e, e); //$NON-NLS-1$
		}
		catch (final IOException e) {
			throw new AOException("Error en la llamada de postfirma al servidor: " + e, e); //$NON-NLS-1$
		}
		
		// Analizamos la respuesta del servidor
		final String stringTrimmedResult = new String(triSignFinalResult).trim();
		if (!stringTrimmedResult.startsWith(SUCCESS)) {
			throw new AOException("La firma trifasica no ha finalizado correctamente: " + new String(triSignFinalResult)); //$NON-NLS-1$
		}
		
		// Los datos no se devuelven, se quedan en el servidor
		try {
			return Base64.decode(stringTrimmedResult.replace(SUCCESS + " NEWID=", ""), Base64.URL_SAFE); //$NON-NLS-1$ //$NON-NLS-2$
		}
		catch (final IOException e) {
			LOGGER.warning("El resultado de NEWID del servidor no estaba en Base64: " + e); //$NON-NLS-1$
			throw new AOException("El resultado devuelto por el servidor no es correcto", e); //$NON-NLS-1$
		}
	}
}