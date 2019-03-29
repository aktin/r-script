package org.aktin.scripting.r;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Takes data files from DataExtractor (patient, encounter, etc.) and runs an R
 * script via the command {@code Rscript}.
 * <p>
 * The script should write all generated output (e.g. plots, diagrams, tables)
 * to files in the working directory. The script should not output anything on
 * stdout or stderr. Any output on stdout is treated as warnings, any output on
 * stderr is treated as error messages.
 * 
 * @author R.W.Majeed
 *
 */
public class RScript {
	private static final Logger log = Logger.getLogger(RScript.class.getName());

	/** executable path of the Rscript command */
	private Path rScriptExecutable;

//	/**
//	 * Constructs the class. Locates the Rscript executable and verifies that
//	 * all dependencies are available.
//	 */
//	public RScript() {
//		// TODO this works only for testing on windows not for deployment
//		if (System.getProperty("os.name").substring(0, 7).equals("Windows")) { // hoping
//																				// this
//																				// will
//																				// work
//																				// on
//																				// every
//																				// Windows
//																				// Version
//			FileSystem fs = FileSystems.getDefault();
//			this.rScriptExecutable = fs.getPath("C:/Program Files/R/R-3.2.4revised/bin/Rscript.exe");
//			// System.out.println(rScriptExecutable.toString());
//		}
//	}

	public RScript(Path executable) {
		this.rScriptExecutable = executable;
	}

	public String getVersion() throws IOException{
		ProcessBuilder pb = new ProcessBuilder(rScriptExecutable.toString(), "--version");
		Path temp = Files.createTempDirectory("r-version");
		pb.directory(temp.toFile());
		Process process = pb.start();
		int exitCode;
		try {
			if( !process.waitFor(500, TimeUnit.MILLISECONDS) ) {
				// process did not terminate within the specified time
				process.destroyForcibly();
				throw new IOException("R process did not terminate in the specified time");
			}else {
				exitCode = process.exitValue();
			}
		} catch (InterruptedException e) {
			throw new IOException("Interrupted during R execution", e);
		}
		InputStream output = process.getInputStream();
		InputStream error = process.getErrorStream();
		String value = convertStreamToString(error);
		if( value == null || value.length() == 0 ) {
			// use stdout
			value = convertStreamToString(output);
		}
		output.close();
		error.close();
		try {
			Files.delete(temp);			
		}catch( IOException e ) {
			log.log(Level.WARNING, "Unable to delete temp directory "+temp, e);
		}
		if( exitCode != 0 ) {
			throw new IOException("Non-zero exit code");
		}
		return  value;
	}

	private static final String RSCRIPT_OUTPUT_PACKAGE_VERSIONS="write.table(x=subset(x=data.frame(installed.packages(noCache=TRUE)),select=Version,is.na(Priority) | Priority != 'base' | Package=='base'),quote=FALSE,sep='\\t',col.names=FALSE,file='versions.txt')";
	private static final String VERSIONS_SCRIPT_NAME = "versions.R";

	public Map<String, String> getPackageVersions() throws IOException{
		ProcessBuilder pb = new ProcessBuilder(rScriptExecutable.toString(), "--vanilla", VERSIONS_SCRIPT_NAME);
		Path temp = Files.createTempDirectory("r-version");
		Path source = temp.resolve(VERSIONS_SCRIPT_NAME);
		try( BufferedWriter w = Files.newBufferedWriter(source,StandardOpenOption.CREATE_NEW) ){
			w.write(RSCRIPT_OUTPUT_PACKAGE_VERSIONS);
		}
		pb.directory(temp.toFile());
		Process process = pb.start();
		int exitCode;
		try {
			if( !process.waitFor(2000, TimeUnit.MILLISECONDS) ) {
				// process did not terminate within the specified time
				process.destroyForcibly();
				throw new IOException("R process did not terminate in the specified time");
			}else {
				exitCode = process.exitValue();
			}
		} catch (InterruptedException e) {
			throw new IOException("Interrupted during R execution", e);
		}
		
		InputStream output = process.getInputStream();
		InputStream error = process.getErrorStream();
		String value = convertStreamToString(error);
		if( value == null || value.length() == 0 ) {
			// use stdout
			value = convertStreamToString(output);
		}
		output.close();
		error.close();
		Map<String, String> map = null;
		if( exitCode == 0 ) {
			// read versions output
			Path versionTable = temp.resolve("versions.txt");
			try( BufferedReader rd = Files.newBufferedReader(versionTable) ){
				map = new HashMap<String,String>();
				String line;
				while( (line = rd.readLine()) != null ) {
					int t = line.indexOf('\t');
					if( t == -1 ){
						continue;
					}
					map.put(line.substring(0, t), line.substring(t+1, line.length()));
				}
			}
			Files.delete(versionTable);
		}
		try {
			Files.delete(source);
			Files.delete(temp);			
		}catch( IOException e ) {
			log.log(Level.WARNING, "Unable to delete temp directory "+temp, e);
		}
		if( exitCode != 0 ) {
			log.warning("R stderr: "+value);
			throw new IOException("Non-zero exit code "+exitCode);
		}
		return map;
		
	}
	// TODO configure timeout milliseconds to wait for process to exit
	public void runRscript(Path workingDir, String mainScript) throws IOException {
		// log.info("RScript Start");
		// log.info(mainScript.toString());
		// log.info(workingDir.toString());
		// log.info(workingDir.resolve(mainScript).toString());
		// log.info(rScriptExecutable.toString());
		ProcessBuilder pb = new ProcessBuilder(rScriptExecutable.toString(), "--vanilla", mainScript);
		pb.directory(workingDir.toFile());

		Process process = pb.start();
		// get the error stream of the process and print it
		InputStream error = process.getErrorStream();

		// get the output stream of the process and print it
		InputStream output = process.getInputStream();

		int exitCode;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			throw new IOException("Interrupted during R execution", e);
		}
		if( exitCode != 0 ){
			// Rscript did not terminate successfully
			// something went wrong
			if (error.available() > 0) {
				String stderr = convertStreamToString(error);
				log.warning("Rscript stderr: "+stderr);
				// use first line of output for exception
				int nl = stderr.indexOf('\n'); // find first newline
				if( nl != -1 ){ // if there, reduce to first line
					stderr = stderr.substring(0, nl).trim();
				}
				// TODO use maximum of newline or first 250 characters
				throw new IOException("R execution failed w/ exit code "+exitCode+": "+stderr);
			}else{
				throw new IOException("R execution failed w/ exit code "+exitCode+", no stderr");
			}
		}
		error.close();

		if (output.available() > 0) {
			log.warning("non-empty R stdout: "+convertStreamToString(output));
		}
		// read output stream
		
		output.close();
	}

	// debugging only
	public String convertStreamToString(InputStream is) throws IOException {
		// To convert the InputStream to String we use the
		// Reader.read(char[] buffer) method. We iterate until the
		// Reader return -1 which means there's no more data to
		// read. We use the StringWriter class to produce the string.
		if (is != null) {
			Writer writer = new StringWriter();

			char[] buffer = new char[1024];
			try {
				Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				int n;
				while ((n = reader.read(buffer)) != -1) {
					writer.write(buffer, 0, n);
				}
			} finally {
				is.close();
			}
			return writer.toString();
		}
		return "";
	}

}
