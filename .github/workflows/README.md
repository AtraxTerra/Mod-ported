# Why this branch carries a workflow

`main` holds no code; the port lives on `port/neoforge-26.2`.

But GitHub only honours the `schedule` and `workflow_dispatch` triggers for workflow files on the
**default branch**. A scheduled workflow sitting only on `port/neoforge-26.2` never fires, and
cannot be run manually either. So `upstream-sync.yml` lives here as well, and checks out the port
branch itself (`ref: port/neoforge-26.2`) to do its work.

`build.yml` is deliberately *not* here: it runs on `push` and `pull_request`, and those triggers use
the workflow file from the ref being pushed, so it works from the port branch alone. Copying it here
would only make GitHub try to build `main`, which has no Gradle project.

Keep this file and `upstream-sync.yml` in step with the copy on `port/neoforge-26.2`.
