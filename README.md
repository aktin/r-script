# RScript Java Wrapper

This project provides a lightweight Java wrapper around the `Rscript` executable, enabling the execution of R scripts from within a Java environment.
The Rscripts used for the AKTIN Monatsberichte (AKTIN montly reports) can be found [here](reports/aktin-monthly/src/main/resources).
## Features

- Execute R scripts from Java with process timeout and error handling
- Retrieve the installed R version (`Rscript --version`)
- Extract installed R packages and their versions as a Java Map
- Detects abnormal R script termination and surfaces error messages
- Includes JUnit tests for typical use cases (version check, timeout, stderr handling)

## Usage

### Instantiating the Wrapper

```java
import java.nio.file.Paths;
import org.aktin.scripting.r.RScript;

RScript rscript = new RScript(Paths.get("/usr/bin/Rscript"));
