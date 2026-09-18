$xml = [xml](Get-Content 'e:\Projects\Presupuesto\_window425.xml' -Encoding UTF8)
$xml.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object { '{0}|{1}|{2}|{3}' -f $_.text, $_.'content-desc', $_.enabled, $_.bounds }
