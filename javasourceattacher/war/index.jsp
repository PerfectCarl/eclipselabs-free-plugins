<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">
<!-- The HTML 4.01 Transitional DOCTYPE declaration-->
<!-- above set at the top of the file will set     -->
<!-- the browser's rendering engine into           -->
<!-- "Quirks Mode". Replacing this declaration     -->
<!-- with a "Standards Mode" doctype is supported, -->
<!-- but may lead to some differences in layout.   -->

<html>
  <head>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
	<script type="text/javascript" src="http://code.jquery.com/jquery-1.7.1.min.js"></script>
    <title>Hello App Engine</title>
  </head>

  <body>
    <h1>Hello App Engine!</h1>

    <table>
      <tr>
        <td colspan="2" style="font-weight:bold;">Available Servlets:</td>
      </tr>
      <tr>
        <td><a href="helloappengine">HelloAppEngineServlet</a></td>
      </tr>
    </table>
    <script type="text/javascript">
$(function(){

$.getJSON('${pageContext.request.contextPath}/rest/bundles/1', function(data) {
		console.dir(data)	  
});	

$.getJSON('${pageContext.request.contextPath}/rest/bundles', function(data) {
	console.dir(data)	  
});	

$.getJSON('${pageContext.request.contextPath}/rest/bundles', {sourceMd5 : "src123456"}, function(data) {
	console.dir(data)	  
});	


});


    </script>
  </body>
</html>
