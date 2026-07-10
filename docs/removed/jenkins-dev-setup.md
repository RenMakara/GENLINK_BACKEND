Setup guide: Jenkins job for only the `dev` branch

This document shows exact steps to create a Jenkins Pipeline job that only builds `dev` on push.

Checklist
- Create a Pipeline job in Jenkins (Pipeline from SCM) or create the job with the provided XML.
- Make sure Jenkins can reach the Git host (public URL or webhook reachability).
- Add a webhook in your Git host for push events to the Jenkins webhook endpoint.
- Push to `dev` to trigger a build and verify in Jenkins.

Option A — UI (Pipeline from SCM)
1. In Jenkins: New Item -> choose "Pipeline" -> Enter a name (e.g. `backend-dev`).
2. Select "Pipeline" and click OK.
3. In the job configuration -> Pipeline section:
   - Definition: "Pipeline from SCM"
   - SCM: Git
   - Repository URL: (your repo URL)
   - Credentials: add/select credentials if private
   - Branch Specifier: */dev
   - Script Path: Jenkinsfile
4. Save.
5. Add webhook on your Git host (see below) to notify Jenkins on push.

Option B — Create job by uploading XML (automation)
1. Edit `docs/jenkins-dev-job-config.xml` and replace `REPO_URL` and `CREDENTIALS_ID` with your values.
2. Create the job by POSTing the XML to Jenkins (PowerShell example):

```powershell
# Variables to fill
$jenkinsUrl = 'http://JENKINS_URL'
$jobName = 'backend-dev'
$username = 'your-jenkins-username'
$apiToken = 'your-api-token'
$configXmlPath = 'docs/jenkins-dev-job-config.xml'

# Get crumb (CSRF protection)
$crumbResponse = Invoke-RestMethod -Uri "$jenkinsUrl/crumbIssuer/api/json" -Credential (New-Object System.Management.Automation.PSCredential($username,(ConvertTo-SecureString $apiToken -AsPlainText -Force)))
$crumb = $crumbResponse.crumb
$crumbField = $crumbResponse.crumbRequestField

# Create job
Invoke-RestMethod -Uri "$jenkinsUrl/createItem?name=$jobName" -Method Post -Credential (New-Object System.Management.Automation.PSCredential($username,(ConvertTo-SecureString $apiToken -AsPlainText -Force))) -ContentType 'application/xml' -Body (Get-Content $configXmlPath -Raw) -Headers @{ $crumbField = $crumb }
```

If the job exists already, update it using `POST /job/<name>/config.xml` instead.

Webhooks (recommended)
- GitHub
  - Repo -> Settings -> Webhooks -> Add webhook
  - Payload URL: http://<JENKINS_URL>/github-webhook/
  - Content type: application/json
  - Secret: optional — if used, configure same secret in Jenkins GitHub plugin
  - Events: Just the push event

- GitLab
  - Repo -> Settings -> Webhooks
  - URL: http://<JENKINS_URL>/project/backend-dev (or use /gitlab-webhook/ if plugin installed)
  - Trigger: Push events

- Bitbucket
  - Add a repository webhook to http://<JENKINS_URL>/bitbucket-hook/ or via the Bitbucket plugin endpoint.

Verify a build
- Push a trivial commit to `dev`:

```powershell
git checkout dev
git pull origin dev
# make a small change
git commit -am "ci: test webhook"
git push origin dev
```

- In Jenkins: Open job `backend-dev` -> Build History -> click the latest build -> Console Output.
- In Git host: check webhook delivery logs (GitHub -> Webhooks -> Recent Deliveries).

Troubleshooting
- If webhook deliveries fail: ensure Jenkins is reachable (public URL or use ngrok/tunnel).
- If Jenkins responds 403: fetch crumb as shown above or configure GitHub plugin credentials and CSRF settings.
- If wrong branch builds: check Branch Specifier is `*/dev`.

If you want, I can:
- Fill `docs/jenkins-dev-job-config.xml` for you using a provided `REPO_URL` and `CREDENTIALS_ID` (I will not accept secrets here).
- Or, if you provide Jenkins URL, username and API token securely now, I can create the Jenkins job for you (I’ll only use them in this session to call the Jenkins REST API). Which do you prefer?

