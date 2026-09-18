$xml = [xml](Get-Content 'e:\Projects\Presupuesto\README.md' -Encoding UTF8)
(Get-Content 'e:\Projects\Presupuesto\README.md' -Encoding UTF8)[93..118] | Set-Content 'e:\Projects\Presupuesto\_readme_range.txt' -Encoding UTF8
