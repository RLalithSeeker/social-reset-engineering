Write-Host 'Running Self-Healing Pipeline...' -ForegroundColor Cyan
npx fallow fix --yes
Write-Host 'Dispatching OpenCode for Security Review...' -ForegroundColor Cyan
agency-run "Run a full security review using the security-review skill."
