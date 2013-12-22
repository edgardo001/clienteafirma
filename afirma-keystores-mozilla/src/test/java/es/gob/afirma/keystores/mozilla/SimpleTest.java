package es.gob.afirma.keystores.mozilla;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Enumeration;
import java.util.logging.Logger;

import org.junit.Ignore;
import org.junit.Test;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.misc.Platform;
import es.gob.afirma.keystores.dnie.DnieUnifiedKeyStoreManager;
import es.gob.afirma.keystores.main.common.AOKeyStore;
import es.gob.afirma.keystores.main.common.AOKeyStoreManager;
import es.gob.afirma.keystores.main.common.AOKeyStoreManagerFactory;
import es.gob.afirma.keystores.main.common.KeyStoreUtilities;

/** Pruebas simples de almacenes Mozilla NSS. */
public final class SimpleTest {

    /** Inicio de las pruebas desde consola sin JUnit.
     * @param args */
    public static void main(final String[] args) {
		try {
			SimpleTest.testDirectNssUsage();
		}
		catch (final Exception e) {
		    System.err.println(e.toString());
		}
    }

    /** Prueba de la obtenci&oacute;n de almac&eacute;n y alias con Mozilla NSS.
     * @throws Exception */
    @SuppressWarnings("static-method")
    @Test
    @Ignore
    public void testKeyStoreManagerCreation() throws Exception {
    	final AOKeyStoreManager ksm = AOKeyStoreManagerFactory
		 .getAOKeyStoreManager(AOKeyStore.MOZ_UNI, null,
			"TEST-KEYSTORE",  //$NON-NLS-1$
			null, // PasswordCallback
			null // Parent
		);
    	final String[] aliases = ksm.getAliases();
    	for (final String alias : aliases) {
    		System.out.println(alias);
    	}
    }

    /** Prueba de la obtenci&oacute;n de almac&eacute;n y alias con Mozilla NSS agreg&aacute;ndolo con el controlador DNIe 100& Java.
     * @throws Exception */
    @SuppressWarnings("static-method")
    @Test
    @Ignore
    public void testKeyStoreManagerCreationWithDnieAggregation() throws Exception {
    	AOKeyStoreManager ksm = AOKeyStoreManagerFactory
		 .getAOKeyStoreManager(AOKeyStore.MOZ_UNI, null,
			"TEST-KEYSTORE",  //$NON-NLS-1$
			null, // PasswordCallback
			null // Parent
		);
    	ksm = new DnieUnifiedKeyStoreManager(ksm, null);
    	final String[] aliases = ksm.getAliases();
    	for (final String alias : aliases) {
    		System.out.println(alias);
    	}
    	//System.out.println(ksm.getCertificate("ANF Usuario Activo"));
    	//System.out.println(ksm.getCertificate("CertFirmaDigital"));
    	//System.out.println(ksm.getKeyEntry("CertAutenticacion", new UIPasswordCallback("PIN", null)));
    	System.out.println(ksm.getCertificateChain("CertAutenticacion")[2]); //$NON-NLS-1$
    	System.out.println(ksm.getCertificateChain("ANF Usuario Activo")[0]); //$NON-NLS-1$
    }

    private static void testDirectNssUsage() throws Exception {
    	final KeyStore keyStore = KeyStore.getInstance(
			"PKCS11", //$NON-NLS-1$
			loadNSS(
				"C:\\Users\\tomas\\AppData\\Local\\Temp\\nss", //$NON-NLS-1$
				KeyStoreUtilities.getShort("C:\\Users\\tomas\\AppData\\Local\\Temp\\moznProf").replace("\\", "/") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			)
		);
    	keyStore.load(null, new char[0]);
    	final Enumeration<String> aliases = keyStore.aliases();
    	while (aliases.hasMoreElements()) {
    		System.out.println(aliases.nextElement());
    	}
    }

	static Provider loadNSS(final String nssDirectory, final String mozProfileDir) throws AOException,
    																				InstantiationException,
    																				IllegalAccessException,
    																				InvocationTargetException,
    																				NoSuchMethodException,
    																				ClassNotFoundException {
		final String p11NSSConfigFile = MozillaKeyStoreUtilities.createPKCS11NSSConfigFile(
			mozProfileDir,
			nssDirectory
		);

		Logger.getLogger("es.gob.afirma").info("Configuracion de NSS para SunPKCS11:\n" + p11NSSConfigFile); //$NON-NLS-1$ //$NON-NLS-2$

		Provider p = null;
		try {
			p = (Provider) Class.forName("sun.security.pkcs11.SunPKCS11") //$NON-NLS-1$
					.getConstructor(InputStream.class)
					.newInstance(new ByteArrayInputStream(p11NSSConfigFile.getBytes()));
		}
		catch (final Exception e) {
			// No se ha podido cargar el proveedor sin precargar las dependencias
			// Cargamos las dependencias necesarias para la correcta carga
			// del almacen (en Mac se crean enlaces simbolicos)
			if (Platform.OS.MACOSX.equals(Platform.getOS())) {
				MozillaKeyStoreUtilities.configureMacNSS(nssDirectory);
			}
			else {
				MozillaKeyStoreUtilities.loadNSSDependencies(nssDirectory);
			}

			try {
				p = (Provider) Class.forName("sun.security.pkcs11.SunPKCS11") //$NON-NLS-1$
						.getConstructor(InputStream.class)
						.newInstance(new ByteArrayInputStream(p11NSSConfigFile.getBytes()));
			}
			catch (final Exception e2) {
				e.printStackTrace();
				// Un ultimo intento de cargar el proveedor valiendonos de que es posible que
				// las bibliotecas necesarias se hayan cargado tras el ultimo intento
				p = (Provider) Class.forName("sun.security.pkcs11.SunPKCS11") //$NON-NLS-1$
						.getConstructor(InputStream.class)
						.newInstance(new ByteArrayInputStream(p11NSSConfigFile.getBytes()));
			}
		}

		Security.addProvider(p);

		Logger.getLogger("es.gob.afirma").info("Proveedor PKCS#11 para Firefox anadido"); //$NON-NLS-1$ //$NON-NLS-2$

		return p;
	}

}