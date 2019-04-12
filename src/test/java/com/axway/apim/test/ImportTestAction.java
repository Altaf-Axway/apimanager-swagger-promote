package com.axway.apim.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axway.apim.ImportApp;
import com.axway.apim.lib.CommandParameters;
import com.consol.citrus.actions.AbstractTestAction;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.exceptions.CitrusRuntimeException;
import com.consol.citrus.exceptions.ValidationException;

public class ImportTestAction extends AbstractTestAction {
	
	public static String API_DEFINITION = "apiDefinition";
	public static String API_CONFIG = "apiConfig";
	
	private static Logger LOG = LoggerFactory.getLogger(ImportTestAction.class);
	
	@Override
	public void doExecute(TestContext context) {
		String origApiDefinition 			= context.getVariable(API_DEFINITION);
		String origConfigFile 			= context.getVariable(API_CONFIG);
		String stage				= null;
		String apiDefinition			= null;
		String handleNullAsChange	= "false";
		boolean useEnvironmentOnly = false;
		try {
			stage 				= context.getVariable("stage");
		} catch (CitrusRuntimeException ignore) {};
		if(StringUtils.isNotEmpty(origApiDefinition) && !origApiDefinition.contains("http://") && !origApiDefinition.contains("https://")) {
			apiDefinition = replaceDynamicContentInFile(origApiDefinition, context, createTempFilename(origApiDefinition));
		} else {
			apiDefinition = origApiDefinition;
		}
		String configFile = replaceDynamicContentInFile(origConfigFile, context, createTempFilename(origConfigFile));
		LOG.info("Using Replaced Swagger-File: " + apiDefinition);
		LOG.info("Using Replaced configFile-File: " + configFile);
		LOG.info("API-Manager import is using user: '"+context.replaceDynamicContentInString("${oadminPassword1}")+"'");
		int expectedReturnCode = 0;
		try {
			expectedReturnCode 	= Integer.parseInt(context.getVariable("expectedReturnCode"));
		} catch (Exception ignore) {};
		
		try {
			useEnvironmentOnly 	= Boolean.parseBoolean(context.getVariable("useEnvironmentOnly"));
		} catch (Exception ignore) {};
		
		String enforce = "false";
		String ignoreQuotas = "false";
		String ignoreAdminAccount = "false";
		String clientOrgsMode = CommandParameters.MODE_REPLACE;
		String clientAppsMode = CommandParameters.MODE_REPLACE;;
		
		try {
			enforce = context.getVariable("enforce");
		} catch (Exception ignore) {};
		try {
			ignoreQuotas = context.getVariable("ignoreQuotas");
		} catch (Exception ignore) {};
		try {
			clientOrgsMode = context.getVariable("clientOrgsMode");
		} catch (Exception ignore) {};
		try {
			clientAppsMode = context.getVariable("clientAppsMode");
		} catch (Exception ignore) {};
		try {
			ignoreAdminAccount = context.getVariable("ignoreAdminAccount");
		} catch (Exception ignore) {};
		try {
			handleNullAsChange 	= context.getVariable("handleNullAsChange");
		} catch (Exception ignore) {};
		
		if(stage==null) {
			stage = "NOT_SET";
		} else {
			// We need to prepare the dynamic staging file used during the test.
			String stageConfigFile = origConfigFile.substring(0, origConfigFile.lastIndexOf(".")+1) + stage + origConfigFile.substring(origConfigFile.lastIndexOf("."));
			String replacedStagedConfig = configFile.substring(0, configFile.lastIndexOf("."))+"."+stage+".json";
			// This creates the dynamic staging config file! (For testing, we also support reading out of a file directly)
			replaceDynamicContentInFile(stageConfigFile, context, replacedStagedConfig);
		}
		copyAPIImageToTempDir(configFile, context);

		String[] args;
		if(useEnvironmentOnly) {
			args = new String[] {  
					"-c", configFile, "-s", stage};
		} else {
			args = new String[] { 
					"-a", apiDefinition, 
					"-c", configFile, 
					"-h", context.replaceDynamicContentInString("${apiManagerHost}"), 
					"-p", context.replaceDynamicContentInString("${oadminUsername1}"), 
					"-u", context.replaceDynamicContentInString("${oadminPassword1}"),
					"-s", stage, 
					"-f", enforce, 
					"-iq", ignoreQuotas, 
					"-clientOrgsMode", clientOrgsMode, 
					"-clientAppsMode", clientAppsMode, 
					"-ignoreAdminAccount", ignoreAdminAccount, 
					"-handleNullAsChange", handleNullAsChange};
		}
		LOG.info("Ignoring admin account: '"+ignoreAdminAccount+"' | Enforce breaking change: " + enforce + " | useEnvironmentOnly: " + useEnvironmentOnly);
		int rc = ImportApp.run(args);
		if(expectedReturnCode!=rc) {
			throw new ValidationException("Expected RC was: " + expectedReturnCode + " but got: " + rc);
		}
	}
	
	/**
	 * To make testing easier we allow reading test-files from classpath as well
	 */
	private String replaceDynamicContentInFile(String pathToFile, TestContext context, String replacedFilename) {
		
		File inputFile = new File(pathToFile);
		InputStream is = null;
		OutputStream os = null;
		try {
			if(inputFile.exists()) { 
				is = new FileInputStream(pathToFile);
			} else {
				is = this.getClass().getResourceAsStream(pathToFile);
			}
			if(is == null) {
				throw new IOException("Unable to read swagger file from: " + pathToFile);
			}
			String jsonData = IOUtils.toString(is);
			String filename = pathToFile.substring(pathToFile.lastIndexOf("/")+1); // e.g.: petstore.json, no-change-xyz-config.<stage>.json, 

			String jsonReplaced = context.replaceDynamicContentInString(jsonData);

			os = new FileOutputStream(new File(replacedFilename));
			IOUtils.write(jsonReplaced, os);
			
			return replacedFilename;
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			if(os!=null)
				try {
					os.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
		return null;
	}
	
	private String createTempFilename(String origFilename) {
		String prefix = origFilename.substring(0, origFilename.indexOf(".")+1);
		String suffix = origFilename.substring(origFilename.indexOf("."));
		try {
			File tempFile = File.createTempFile(prefix, suffix);
			tempFile.deleteOnExit();
			return tempFile.getAbsolutePath();
		} catch (IOException e) {
			LOG.error("Cant create temp file", e);
			throw new RuntimeException(e);
		}
	}
	
	private static void copyAPIImageToTempDir(String configFile, TestContext context) {
		// If there is an image relative to original config-file, copy it into the temp dir
		File configFileObject = new File(configFile); // This is the replaced config file
		File apiImage;
		try {
			apiImage = new File(context.getVariable("image"));
		} catch (Exception ignore) { return; } // No image configured, nothing to do
		if(apiImage.exists()) {
			try {
				Files.copy(apiImage.toPath(), new File(configFileObject.getParent()+"/"+apiImage.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				LOG.error("Can't copy api-image into working directory");
				throw new RuntimeException();
			}
		}
	}
}
