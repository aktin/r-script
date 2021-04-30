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
import java.util.concurrent.TimeoutException;
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

	// TODO change to asynchronous implementation via process.onExit
	// method should then return a CompletableFuture<Map<String,String>>
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
			if( !process.waitFor(10, TimeUnit.SECONDS) ) {
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

	private <E extends Exception> void destroyForciblyWaitAndThrow(Process process, E e) throws E {
		Process waiter = process.destroyForcibly();
		boolean finished = false;
		try {
			finished = waiter.waitFor(700, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e1) {
			e.addSuppressed(e1);
		}
		if( finished == false ) {
			log.warning("Unable to destroy the process forcibly in the specified time");
		}
		throw e;
	}
	/**
	 * Run the Rscript executable. Working directory will be set as specified in the arguments,
	 * as well as the main script.
	 * @param workingDir Working directory for the Rscript executable. It must contain at least the file specified in the next argument.
	 * @param mainScript File name for the main script to be executed in the working directory. The directory must contain the specified file name.
	 * @param waitMillis Time to wait for the process to finish. If {@code null} the process will wait without limit.
	 * @throws IOException IO error, e.g. with processing the process' output
	 * @throws TimeoutException the specified timeout elapsed before the process exited
	 * @throws AbnormalTerminationException  process terminated abnormally. the provided script did not execute successfully.
	 *   For exit code and STDERR output see {@link AbnormalTerminationException}.
	 */
	public void runRscript(Path workingDir, String mainScript, Integer waitMillis) throws IOException, TimeoutException, AbnormalTerminationException {
		ProcessBuilder pb = new ProcessBuilder(rScriptExecutable.toString(), "--vanilla", mainScript);
		pb.directory(workingDir.toFile());

		Process process = pb.start();
		// get the error stream of the process and print it
		InputStream error = process.getErrorStream();

		// get the output stream of the process and print it
		InputStream output = process.getInputStream();

		int exitCode = -1;
		try { 
			if( waitMillis != null ) {
				boolean finished = process.waitFor(waitMillis.intValue(), TimeUnit.MILLISECONDS);
				if( finished  == true ) {
					// process finished, get exit value
					exitCode = process.exitValue();
				}else {
					log.warning("Process did not finish in the specified time, trying to kill it..");
					// timeout elapsed. Kill process
					destroyForciblyWaitAndThrow(process, new TimeoutException("Timeout elapsed for Rscript execution"));
				}
			}else{
				// wait without timeout
				exitCode = process.waitFor();
			}
		} catch (InterruptedException e) {
			destroyForciblyWaitAndThrow(process, new IOException("Interrupted during R execution", e));
		}
		if( exitCode != 0 ){
			// Rscript did not terminate successfully
			// something went wrong
			String stderr = null;
			if (error.available() > 0) {
				stderr = convertStreamToString(error);
			}
			throw new AbnormalTerminationException(exitCode, stderr);
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
