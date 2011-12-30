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


    <h1>Bundles:</h1>
    <form action="${pageContext.request.contextPath}/rest/bundles" method="post">
    <table>
      <tr>
        <td>md5:</td><td><input type="text" name="md5"></td>
      </tr>
      <tr>
        <td>sha1:</td><td><input type="text" name="sha1"></td>
      </tr>
      <tr>
        <td>md5First1024Bytes:</td><td><input type="text" name="md5First1024Bytes"></td>
      </tr>
      <tr>
        <td>fileSize:</td><td><input type="text" name="fileSize"></td>
      </tr>
      <tr>
        <td><input type=submit value=Submit></td>
      </tr>
    </table>
    </form>

    <h1>Locations:</h1>
    <form action="${pageContext.request.contextPath}/rest/locations" method="post">
    <table>
      <tr>
        <td>bundleId:</td>
        <td><input type="text" name="bundleId"></td>
      </tr>
      <tr>
        <td>url:</td>
        <td><input type="text" name="url"></td>
      </tr>
      <tr>
        <td><input type=submit value=Submit></td>
      </tr>
    </table>
    </form>
    
    
    <script type="text/javascript">
$(function(){

	$.getJSON('${pageContext.request.contextPath}/rest/bundles/1', function(data) {
			console.dir(data)	  
	});	
	
	$.getJSON('${pageContext.request.contextPath}/rest/bundles', function(data) {
		console.dir(data)	  
	});	

	$.getJSON('${pageContext.request.contextPath}/rest/locations/2', function(data) {
		console.dir(data)	  
	});	
	
	$.getJSON('${pageContext.request.contextPath}/rest/locations', function(data) {
		console.dir(data)	  
	});	


});


    </script>    
  </body>
</html>
