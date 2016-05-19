package br.com.softplan.security.zap.maven;

import java.io.File;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import br.com.softplan.security.zap.api.model.AnalysisInfo;
import br.com.softplan.security.zap.api.model.AnalysisType;
import br.com.softplan.security.zap.api.model.AuthenticationInfo;
import br.com.softplan.security.zap.api.model.SeleniumDriver;
import br.com.softplan.security.zap.api.report.ZapReport;
import br.com.softplan.security.zap.api.report.ZapReportUtil;
import br.com.softplan.security.zap.commons.ZapInfo;

/**
 * Abstract Mojo used as a base for the other ZAP Mojos. 
 * 
 * @author pdsec
 */
public abstract class ZapMojo extends AbstractMojo {
	
	// Common
	/**
     * Disables the plug-in execution.
     */
    @Parameter(property = "zap.skip", defaultValue = "false") private boolean skip;
    
	// Analysis
	@Parameter(required=true) private String targetUrl;
	@Parameter private String spiderStartingPointUrl;
	@Parameter private String activeScanStartingPointUrl;
	@Parameter private String[] context;
	@Parameter private String[] technologies;
	@Parameter(defaultValue="480")   private int analysisTimeoutInMinutes;
	@Parameter(defaultValue="false") private boolean shouldRunAjaxSpider;
	@Parameter(defaultValue="false") private boolean shouldRunPassiveScanOnly;
	@Parameter(defaultValue="true")  private boolean shouldStartNewSession;
	
	// ZAP
	/**
	 * Port where ZAP is running or will run.
	 */
	@Parameter(required=true) private Integer zapPort;
	
	/**
	 * Host where ZAP is running.
	 */
	@Parameter(defaultValue="localhost") private String zapHost;
	
	/**
	 * API key needed to access ZAP's API, in case it's enabled.
	 */
	@Parameter(defaultValue="") private String zapApiKey;
	
	/**
	 * Absolute path where ZAP is installed, used to automatically start ZAP.
	 */
	@Parameter private String zapPath;
	
	/**
	 * JVM options used to launch ZAP.
	 */
	@Parameter(defaultValue="-Xmx512m") private String zapJvmOptions;
	
	/**
	 * Options that will be used to automatically start ZAP.
	 */
	@Parameter(defaultValue=ZapInfo.DEFAULT_OPTIONS) private String zapOptions;
	
	/**
	 * Indicates whether ZAP should be automatically started with Docker.
	 */
	@Parameter(defaultValue="false") private boolean shouldRunWithDocker;
	
	/**
	 * ZAP's automatic initialization timeout in milliseconds.
	 */
	@Parameter(defaultValue="120000") private Integer initializationTimeoutInMillis;
	
	/**
	 * Absolute or relative path where the generated reports will be saved.
	 */
	@Parameter private File reportPath;

	// Authentication
	@Parameter private String authenticationType;
	@Parameter private String loginUrl;
	@Parameter private String username;
	@Parameter private String password;
	@Parameter private String extraPostData;
	@Parameter private String loggedInRegex;
	@Parameter private String loggedOutRegex;
	@Parameter private String[] excludeFromScan;
	
	// CAS
	@Parameter private String[] protectedPages;

	// Form and Selenium
	@Parameter(defaultValue="username") private String usernameParameter;
	@Parameter(defaultValue="password") private String passwordParameter;
	
	@Parameter private String[] httpSessionTokens;
	@Parameter(defaultValue="firefox") private String seleniumDriver;
	
	// HTTP
	@Parameter private String hostname;
	@Parameter private String realm;
	@Parameter(defaultValue="80") private int port;
	
	protected ZapInfo buildZapInfo() {
		return ZapInfo.builder()
				.host   (zapHost)
				.port   (zapPort)
				.apiKey (zapApiKey)
				.path   (zapPath)
				.jmvOptions(zapJvmOptions)
				.options(zapOptions)
				.initializationTimeoutInMillis((long) initializationTimeoutInMillis)
				.shouldRunWithDocker(shouldRunWithDocker)
				.build();
	}
	
	protected AuthenticationInfo buildAuthenticationInfo() {
		if (authenticationType == null) {
			return null;
		}
		return AuthenticationInfo.builder()
				.type             (authenticationType)
				.loginUrl         (loginUrl)
				.username         (username)
				.password         (password)
				.extraPostData    (extraPostData)
				.loggedInRegex    (loggedInRegex)
				.loggedOutRegex   (loggedOutRegex)
				.excludeFromScan  (excludeFromScan)
				.protectedPages   (protectedPages)
				.usernameParameter(usernameParameter)
				.passwordParameter(passwordParameter)
				.loginRequestData()
				.httpSessionTokens(httpSessionTokens)
				.seleniumDriver(SeleniumDriver.valueOf(seleniumDriver.toUpperCase()))
				.hostname(hostname)
				.realm(realm)
				.port(port)
				.build();
	}
	
	protected AnalysisInfo buildAnalysisInfo() {
		AnalysisType analysisType = AnalysisType.WITH_SPIDER;
		if (shouldRunAjaxSpider && shouldRunPassiveScanOnly) {
			analysisType = AnalysisType.SPIDER_AND_AJAX_SPIDER_ONLY;
		} else {
			if (shouldRunAjaxSpider) {
				analysisType = AnalysisType.WITH_AJAX_SPIDER;
			}
			if (shouldRunPassiveScanOnly) {
				analysisType = AnalysisType.SPIDER_ONLY;
			}
		}
		return buildAnalysisInfo(analysisType);
	}
	
	protected AnalysisInfo buildAnalysisInfo(AnalysisType analysisType) {
		return AnalysisInfo.builder()
				.targetUrl(targetUrl)
				.spiderStartingPointUrl(spiderStartingPointUrl)
				.activeScanStartingPointUrl(activeScanStartingPointUrl)
				.context(context)
				.technologies(technologies)
				.analysisTimeoutInMinutes(analysisTimeoutInMinutes)
				.analysisType(analysisType)
				.shouldStartNewSession(shouldStartNewSession)
				.build();
	}
	
	protected void saveReport(ZapReport zapReport) {
		getLog().info("Saving Reports...");
		if (reportPath != null) {
			ZapReportUtil.saveAllReports(zapReport, reportPath.getAbsolutePath());
		} else {
			ZapReportUtil.saveAllReports(zapReport);
		}
	}
	
	protected String getTargetUrl() {
		return this.targetUrl;
	}
	
	@Override
	public final void execute() throws MojoExecutionException, MojoFailureException {
		if (skip) {
            getLog().info( "Zap is skipped." );
            return;
        }
		
		doExecute();
	}
	
	public abstract void doExecute() throws MojoExecutionException, MojoFailureException;
	
}
