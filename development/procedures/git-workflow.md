# Procédure — Workflow Git

## Branches
- `main` : stable
- `feature/*` : développements

## Cycle court
1. Créer une branche : `feature/xxx`
2. Commit petits et descriptifs
3. Push + PR (si tu utilises PR)

## Commandes utiles
```powershell
cd "C:\Users\patri\AndroidStudioProjects\engagementjudo"

git status

# nouvelle feature
git checkout -b feature/nom

# commit
git add -A
git commit -m "feat: ..."

# push
git push -u origin feature/nom
```

