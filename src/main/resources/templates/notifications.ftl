<!DOCTYPE html>
<html lang="en">
<body>
<p>
    Hello, ${destinationName}. There are ${bookmarkCount} outstanding CFPs to process this (${year}).
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