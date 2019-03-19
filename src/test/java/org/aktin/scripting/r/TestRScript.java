package org.aktin.scripting.r;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.aktin.dwh.PreferenceKey;
import org.junit.Assert;
import org.junit.Test;

public class TestRScript {
	static final String[] rPathSearch = {
			"C:\\Program Files\\R\\R-3.3.1\\bin\\Rscript.exe",
			"C:\\Program Files\\R\\R-3.2.2\\bin\\Rscript.exe",
			"C:\\Program Files\\R\\R-3.2.0\\bin\\x64\\Rscript.exe",
			"C:\\Program Files\\R\\R-3.2.0\\bin\\Rscript.exe",
			"C:\\Program Files\\R\\R-3.4.0\\bin\\Rscript.exe",
			"C:\\Program Files\\R\\R-3.5.1\\bin\\Rscript.exe"
			// TODO add search paths for compilation on Linux
	};
	public static Path findR(){
		// try system property 'rscript.binary'
		Path p;
		String path = System.getProperty(PreferenceKey.rScriptBinary.key());
		if( path != null ){
			p = Paths.get(path);
			if( !Files.isExecutable(p) ){
				Assert.fail("System property '"+PreferenceKey.rScriptBinary.key()+"' defined, but target not found/executable.");
			}
			return p;
		}
		// try windows path
		for( String binary : rPathSearch ){
			p = Paths.get(binary);
			if( Files.isExecutable(p) ){
				return p;
			}
		}
		Assert.fail("Path to Rscript not found. Please edit TestReportGeneration.java or define a (local) system property: "+PreferenceKey.rScriptBinary.key());
		return null;
	}

	@Test
	public void testRetrieveVersions() throws IOException {
		Path p = findR();
		RScript r = new RScript(p);
		String version = r.getVersion();
		Assert.assertNotNull(version);
		Assert.assertNotEquals("", version);
		System.out.println("R --version output: "+version);
		Map<String, String> packages = r.getPackageVersions();

		String baseVersion = packages.get("base");
		Assert.assertNotNull(baseVersion);
		// base version should be part of the version string
		Assert.assertTrue(version.indexOf(baseVersion) != -1);
		packages.forEach( (k,v) -> System.out.println("R package "+k+": "+v) );
	}

}
