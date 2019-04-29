package org.aktin.scripting.r;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.TimeoutException;

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
	
	@Test
	public void verifyExecutionTimeout() throws IOException {
		Path p = findR();
		RScript r = new RScript(p);
		
		Path dir = Files.createTempDirectory("r-script-test");
		Path script = dir.resolve("main.R");
		try( BufferedWriter w = Files.newBufferedWriter(script, StandardOpenOption.CREATE_NEW) ){
			w.write("Sys.sleep(10)\n");
		}
		// verify that we don't need to wait for the process to exit
		long start = System.currentTimeMillis();
		try {
			r.runRscript(dir, script.getFileName().toString(), 1000);
			Assert.fail("Process should not have been termianted regularly");
		} catch (TimeoutException e) {
			// this is what we expect!
			// fall through to outside of try
		} catch (AbnormalTerminationException e) {
			Assert.fail();
		}
		long elapsed = System.currentTimeMillis() - start;
		// remove directories
		try{
			Files.delete(script);
			Files.delete(dir);
		}catch( IOException e ) {
			System.err.println("Unable to delete temporary script because process is still alive");
		}
		// verify early termination
		Assert.assertTrue("Process should be terminated earlier", elapsed < 3000);
	}

	@Test
	public void verifyAbnormalTerminationStderr() throws IOException {
		Path p = findR();
		RScript r = new RScript(p);
		
		Path dir = Files.createTempDirectory("r-script-test");
		Path script = dir.resolve("main.R");
		try( BufferedWriter w = Files.newBufferedWriter(script, StandardOpenOption.CREATE_NEW) ){
			w.write("this.function.does.no.exist()\n");
		}
		try {
			r.runRscript(dir, script.getFileName().toString(), 1000);
			Assert.fail("Process should not have been termianted regularly");
		} catch (TimeoutException e) {
			// no timeout expected
			Assert.fail();
		} catch (AbnormalTerminationException e) {
			// this is what we want!
			Assert.assertEquals(1, e.getExitCode());
			String eout = e.getErrorOutput();
			Assert.assertTrue("Unexpected R stderr "+eout,eout.contains("\"this.function.does.no.exist\""));
		}
	}
}
