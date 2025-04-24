@echo off
set PROJECT_DIR= C:\Users\nicho\IdeaProjects\CNT5106_P2P_Project

start cmd /k "cd /d %PROJECT_DIR% && java PeerProcess 1001"
start cmd /k "cd /d %PROJECT_DIR% && java PeerProcess 1002"
start cmd /k "cd /d %PROJECT_DIR% && java PeerProcess 1003"
start cmd /k "cd /d %PROJECT_DIR% && java PeerProcess 1004"