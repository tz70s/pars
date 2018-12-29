#!/bin/bash

set -e

git config --global user.email $GITHUB_EMAIL
git config --global user.name $GITHUB_NAME
git config --global push.default simple

git remote rm origin
git remote add origin https://tz70s:$GITHUB_TOKEN@github.com/tz70s/task4s.git

# In order to avoid commit overwhelming, a temporary work around is delete gh-page branch and reconstruct a new one.
git push origin --delete gh-pages

# Create a new one.
git checkout --orphan gh-pages
git rm --cached $(git ls-files)
git commit --allow-empty -m "Initialized github page."
git push origin gh-pages

git checkout -f master

sbt site/publishMicrosite