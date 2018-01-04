<!DOCTYPE html>
<html lang="en">
<body>
<p>
 Hello, ${destinationName}. There are still ${bookmarkCount} outstanding CFPs to process in ${year}.
</P>
<ol>
<#list bookmarks as b>
    <li>
        <a href="${b.href}"> ${b.description} </a>
    </li>
</#list>
</ol>
</body>
</html>