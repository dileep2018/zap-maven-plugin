/*
 * This script is intended to handle CAS (http://jasig.github.io/cas) authentication via ZAP.
 *
 * When working with CAS, a single POST request with the credentials is not enough to trigger the authentication.
 * When we GET the login page, some input values used by CAS are generated (the login ticket and some Spring Web
 * Flow related parameters), and they must be included in the POST request for the authentication to work. So
 * this script basically sends a GET to the login page, parses its response looking for the values generated by
 * CAS, and sends a POST request with these values and the credentials.
 * 
 * This is enough to trigger the authentication, but it's not enough to enable a successfull authenticated scan
 * with ZAP. There is one more step needed because of redirects: CAS loves them and ZAP doesn't. More details on
 * that can be found in the comments within the script.
 * 
 * Reauthentication works and a good way to achieve it is with a Logged Out Regex as something like:
 *   \QLocation: http://your.domain/cas-server/\E.*
 * Unauthenticated responses will be 302 redirects to the CAS server, so this is the easiest way to identify that
 * there was a redirect to the CAS server and thus the user is not logged in.
 * 
 * @author Thiago Porciúncula <thiago.porciuncula at softplan.com.br>
 * @author Hugo Baes <hugo.junior at softplan.com.br>
 * @author Fábio Resner <fabio.resner at softplan.com.br>
 */
function authenticate(helper, paramsValues, credentials) {
    print("---- CAS authentication script has started ----");
    
    // Enable Rhino behavior, in case ZAP is running on Java 8 (which uses Nashorn)
    if (java.lang.System.getProperty("java.version").startsWith("1.8")) {
        load("nashorn:mozilla_compat.js");
    }
    
    // Imports
    importClass(org.parosproxy.paros.network.HttpRequestHeader)
    importClass(org.parosproxy.paros.network.HttpHeader)
    importClass(org.apache.commons.httpclient.URI)
    importClass(org.apache.commons.httpclient.params.HttpClientParams)
    importClass(java.util.regex.Pattern)
    
    var loginUri = new URI(paramsValues.get("loginUrl"), false);
    
    // Perform a GET request to the login page to get the values generated by CAS on the response (login ticket, event id, etc)
    var get = helper.prepareMessage();
    get.setRequestHeader(new HttpRequestHeader(HttpRequestHeader.GET, loginUri, HttpHeader.HTTP10));
    helper.sendAndReceive(get);
    var casInputValues = getCASInputValues(get.getResponseBody().toString());
    
    // Build the request body using the credentials values and the CAS values obtained from the first request
    var requestBody  = "username="   + encodeURIComponent(credentials.getParam("username"));
    requestBody     += "&password="  + encodeURIComponent(credentials.getParam("password"));
    requestBody     += "&lt="        + encodeURIComponent(casInputValues["lt"]);
    requestBody     += "&execution=" + encodeURIComponent(casInputValues["execution"]);
    requestBody     += "&_eventId="  + encodeURIComponent(casInputValues["_eventId"]);
    
    // Add any extra post data provided
    extraPostData = paramsValues.get("extraPostData");
    if (extraPostData != null && !extraPostData.trim().isEmpty()) { 
        requestBody += "&" + extraPostData.trim();
    }
    
    // Perform a POST request to authenticate
    print("POST request body built for the authentication:\n  " + requestBody.replaceAll("&", "\n  "));
    var post = helper.prepareMessage();
    post.setRequestHeader(new HttpRequestHeader(HttpRequestHeader.POST, loginUri, HttpHeader.HTTP10));
    post.setRequestBody(requestBody);
    helper.sendAndReceive(post);
    
    /*
     * At this point we are authenticated, but we are not done yet :(
     * 
     * We have authenticated on the CAS server (let's say http://mydomain/cas-server), so when we access
     * the app (i.e. http://mydomain/my-app) for the first time, we will be redirected to the CAS server.
     * Since we are authenticated, the CAS server will redirect us back to the app:
     * 
     * http://your.domain/your-app -> http://your.domain/cas-server -> http://your.domain/your-app
     * 
     * There are two problems here: the first is that ZAP's Spider explicitly doesn't follow redirects.
     * So if the Spider access the app, it will get a redirect response and the page will never really
     * be spidered. However, this redirect happens only at the first time we access the app after the
     * authentication, so we could just access the app once before ZAP's Spider starts, right? Almost.
     * This is a circular redirect, and the second problem is that ZAP doesn't support it. So, this
     * strategy works, but we need a bit of code to get us through this.
     * 
     * This script has another parameter that should hold at least one protected page for each app that
     * might be analyzed. We then proceed to enable circular redirect for ZAP (reflection to the rescue!)
     * and do a simple GET for each page, ensuring no redirects will happen during our analysis.
     */
    
    // Get the protected pages
    var protectedPagesSeparatedByComma = paramsValues.get("protectedPages");
    var protectedPages = protectedPagesSeparatedByComma.split(",");
    
    // Enable circular redirect
    var client = getHttpClientFromHelper(helper);
    client.getParams().setParameter(HttpClientParams.ALLOW_CIRCULAR_REDIRECTS, true);
    
    // Perform a GET request on the protected pages to avoid redirects during the scan
    for (index in protectedPages) {
        var request = helper.prepareMessage();
        request.setRequestHeader(new HttpRequestHeader(HttpRequestHeader.GET, new URI(protectedPages[index], false), HttpHeader.HTTP10));
        helper.sendAndReceive(request, true);
    }
    
    // Disable circular redirect
    client.getParams().setParameter(HttpClientParams.ALLOW_CIRCULAR_REDIRECTS, false);
    
    print("---- CAS authentication script has finished ----\n");
    
    return post;
}

function getCASInputValues(response){
    var result = {};
    
    var regex = "<input.*name=\"(lt|execution|_eventId)\".*value=\"([^\"]*)\"";
    var matcher = Pattern.compile(regex).matcher(response);
    while (matcher.find()) {
        result[matcher.group(1)] = matcher.group(2);
    }
    
    return result;
}

function getHttpClientFromHelper(helper) {
    var httpSenderField = helper.getClass().getDeclaredField("httpSender");
    httpSenderField.setAccessible(true);	
    var httpSender = httpSenderField.get(helper);
    
    var clientField = httpSender.getClass().getDeclaredField("client");
    clientField.setAccessible(true);
    return clientField.get(httpSender);
}

function getRequiredParamsNames() {
    return ["loginUrl", "protectedPages"];
    // The protectedPages parameter will normally have a value similar to http://your.domain/your-app/protected/index.html.
    // If your CAS server happens to handle authentication for more than one app (which is the usual case), and you intend
    // to scan those apps as well, make sure to provide one protected page for each app separated by comma.
}

function getOptionalParamsNames() {
    return ["extraPostData"];
}

function getCredentialsParamsNames() {
    return ["username", "password"];
}
