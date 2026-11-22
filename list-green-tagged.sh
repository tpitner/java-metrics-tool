#!/bin/zsh
for i in bibliography/*.pdf(N); do
    if mdls -raw -name kMDItemUserTags "$i" | grep -q 'Zelena'; then
        echo "${i:t}"
    fi
done