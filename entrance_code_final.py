from typing import List

def entrance_code(playerID: List[int], registration: List[int], P: int) -> List[int]:
    m, n = len(playerID), len(registration)
    dp = [[[[] for _ in range(P + 1)] for _ in range(n + 1)] for _ in range(m + 1)]
    
    for i in range(m + 1):
        for j in range(n + 1):
            dp[i][j][0] = []
    
    for i in range(m + 1):
        for j in range(n + 1):
            for k in range(1, P + 1):
                if i + j < k:
                    continue
                    
                candidates = []
                
                if i > 0:
                    candidates.append(dp[i-1][j][k])
                
                if j > 0:
                    candidates.append(dp[i][j-1][k])
                
                if i > 0 and k > 0:
                    prev = dp[i-1][j][k-1]
                    candidates.append(prev + [playerID[i-1]])
                
                if j > 0 and k > 0:
                    prev = dp[i][j-1][k-1] 
                    candidates.append(prev + [registration[j-1]])
                
                valid_candidates = [c for c in candidates if len(c) == k]
                if valid_candidates:
                    dp[i][j][k] = max(valid_candidates, key=lambda x: x)
                else:
                    dp[i][j][k] = []
    
    return dp[m][n][P]
