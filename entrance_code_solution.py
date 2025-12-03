from typing import List

def entrance_code(playerID: List[int], registration: List[int], P: int) -> List[int]:
    """
    Generate the largest possible entrance code of length P using digits from 
    playerID and registration while maintaining their relative order.
    
    Args:
        playerID: List of digits from player ID
        registration: List of digits from registration number  
        P: Length of the desired entrance code
        
    Returns:
        List of P digits representing the entrance code
    """
    m, n = len(playerID), len(registration)
    
    # dp[i][j][k] = best sequence using first i digits from playerID,
    # first j digits from registration, selecting exactly k digits
    dp = [[[[] for _ in range(P + 1)] for _ in range(n + 1)] for _ in range(m + 1)]
    
    # Base case: no digits selected
    for i in range(m + 1):
        for j in range(n + 1):
            dp[i][j][0] = []
    
    # Fill DP table
    for i in range(m + 1):
        for j in range(n + 1):
            for k in range(1, P + 1):
                if i + j < k:
                    continue
                    
                candidates = []
                
                # Option 1: Don't use current playerID digit
                if i > 0:
                    candidates.append(dp[i-1][j][k])
                
                # Option 2: Don't use current registration digit  
                if j > 0:
                    candidates.append(dp[i][j-1][k])
                
                # Option 3: Use current playerID digit
                if i > 0 and k > 0:
                    prev = dp[i-1][j][k-1]
                    candidates.append(prev + [playerID[i-1]])
                
                # Option 4: Use current registration digit
                if j > 0 and k > 0:
                    prev = dp[i][j-1][k-1] 
                    candidates.append(prev + [registration[j-1]])
                
                # Choose the best option (largest number)
                valid_candidates = [c for c in candidates if len(c) == k]
                if valid_candidates:
                    dp[i][j][k] = max(valid_candidates, key=lambda x: x)
                else:
                    dp[i][j][k] = []
    
    return dp[m][n][P]

# Test with the provided examples
def test_examples():
    # Example 1: Input: 4;9 8 4 2;5;2 6 7 2 6;5
    # Expected Output: 98764
    playerID1 = [9, 8, 4, 2]
    registration1 = [2, 6, 7, 2, 6]
    P1 = 5
    result1 = entrance_code(playerID1, registration1, P1)
    print(f"Example 1: {result1}")  # Should be [9, 8, 7, 6, 4]
    
    # Example 2: Input: 5;1 4 6 8 9;8;9 4 3 2 8 9 5 6;11  
    # Expected Output: 96894328956
    playerID2 = [1, 4, 6, 8, 9]
    registration2 = [9, 4, 3, 2, 8, 9, 5, 6]
    P2 = 11
    result2 = entrance_code(playerID2, registration2, P2)
    print(f"Example 2: {result2}")  # Should be [9, 6, 8, 9, 4, 3, 2, 8, 9, 5, 6]

if __name__ == "__main__":
    test_examples()
