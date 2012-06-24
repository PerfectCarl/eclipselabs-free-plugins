var checksumsearchUrl = WScript.Arguments(0) // http://repo.springsource.org/webapp/checksumsearch.html
var gavsearchUrl = WScript.Arguments(1) // http://repo.springsource.org/webapp/gavcsearch.html
var filesha1 = WScript.Arguments(2) // 2bf96b7aa8b611c177d329452af1dc933e14501c

var ie = WScript.CreateObject("InternetExplorer.Application")
ie.Visible = false
ie.Silent = true

ie.Navigate(checksumsearchUrl)
while (ie.Busy) WScript.Sleep(200)

var query = ie.document.getElementsByName('query').item(1)
query.value = filesha1
var form = query.form
var submit = form.item(':submit').item(0).click()

while (ie.Busy || ie.document.getElementsByTagName('body').item(0).innerText.indexOf('matches') == -1) WScript.Sleep(200)

var link = ''
var links = ie.document.getElementsByTagName('a')
for (var i = 0; i < links.length; i++) {
  if (links.item(i).href.indexOf('.jar') == links.item(i).href.length - 4) link = links.item(i).href
}
if (link != '') {
	var pom = link.substring(0, link.length - 4) + '.pom'

	var xmlDoc = WScript.CreateObject("Microsoft.XMLDOM")
	xmlDoc.async = false
	xmlDoc.load (pom)
	var groupId = xmlDoc.selectNodes('/project/groupId').item(0).text || xmlDoc.selectNodes('/project/parent/groupId').item(0).text
	var artifactId = xmlDoc.selectNodes('/project/artifactId').item(0).text
	var version = xmlDoc.selectNodes('/project/version').item(0).text || xmlDoc.selectNodes('/project/parent/version').item(0).text

	ie.Navigate(gavsearchUrl)
	while (ie.Busy) WScript.Sleep(200)

	ie.document.getElementsByName('groupIdField').item(0) = groupId
	ie.document.getElementsByName('artifactIdField').item(0) = artifactId
	ie.document.getElementsByName('versionField').item(0) = version
	ie.document.getElementsByName('classifierField').item(0) = 'sources'
	ie.document.getElementsByName('classifierField').item(0).form.item(':submit').item(0).click()

	while (ie.Busy || ie.document.getElementsByTagName('body').item(0).innerText.indexOf('matches') == -1) WScript.Sleep(200)

	link = ''
	links = ie.document.getElementsByTagName('a')
	for (var i = 0; i < links.length; i++) {
	  if (links.item(i).href.indexOf('-sources.jar') == links.item(i).href.length - 12) link = links.item(i).href
	}

	WScript.Echo(link)
}
ie.Quit()
