package fr.pantheonsorbonne.cri.bpmn;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * Hello world!
 *
 */
public class GRPCFacade {

	private final static Logger LOGGER = LoggerFactory.getLogger(GRPCFacade.class);
	private final String goPath = System.getenv("GOPATH");
	
	private File openAPISpec;
	
	public GRPCFacade(File openAPISpec) {
		this.openAPISpec=openAPISpec;
	}

	public File generateGRPC() {

		Process process = null;
		try {
			String apiSpecFile = openAPISpec.getName();
			File apiSpecFileDir = openAPISpec.getParentFile();
			File outputDir = Files.createTempDir();
			File grpcOutput = Paths.get(outputDir.getAbsolutePath(), "grpc_server_stub").toFile();
			grpcOutput.mkdirs();
			File targetProtoFile = Paths
					.get(outputDir.toString(),
							String.format("%s.proto", Files.getNameWithoutExtension(openAPISpec.getName()).toString()))
					.toFile();
			{

				ProcessBuilder pb = new ProcessBuilder("gnostic", //
						"--grpc-out=" + outputDir.getAbsolutePath(), //
						apiSpecFile).inheritIO();
				pb.directory(apiSpecFileDir);
				process = pb.start();

				while (process.isAlive())
					Thread.sleep(100);

				if (process.exitValue() != 0) {

					BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));
					var stdout = stdInput.lines().collect(Collectors.joining("\n"));
					throw new RuntimeException(stdout);
				}
			}

			{
				ProcessBuilder pb = new ProcessBuilder("protoc", //
						"-I" + outputDir, //
						"-I" + ".", //
						"-I" + Paths.get(goPath, "src", "github.com", "grpc-ecosystem", "grpc-gateway", "third_party",
								"googleapis").toString(), //
						"--java_out=" + grpcOutput, //
						targetProtoFile.getAbsolutePath()).inheritIO();
				pb.directory(apiSpecFileDir);
				process = pb.start();

				while (process.isAlive())
					Thread.sleep(100);

				if (process.exitValue() != 0) {

					BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));
					var stdout = stdInput.lines().collect(Collectors.joining("\n"));
					throw new RuntimeException(stdout);
				}
			}

			LOGGER.info("gRPC resources created in " + grpcOutput);
			return outputDir;

		} catch (IOException |

				InterruptedException e) {
			e.printStackTrace();
			if (process != null) {
				BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				var stdout = stdInput.lines().collect(Collectors.joining("\n"));

				throw new RuntimeException(stdout);
			} else {
				throw new RuntimeException(e);
			}
		}

	}

}
