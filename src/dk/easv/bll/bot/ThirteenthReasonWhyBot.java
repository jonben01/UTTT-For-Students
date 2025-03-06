package dk.easv.bll.bot;

import dk.easv.bll.bot.IBot;
import dk.easv.bll.field.IField;
import dk.easv.bll.game.GameState;
import dk.easv.bll.game.IGameState;
import dk.easv.bll.move.IMove;
import dk.easv.bll.move.Move;

import java.util.*;

public class ThirteenthReasonWhyBot implements IBot {
    final int moveTimeMs = 1000;
    private String BOT_NAME = "My Thirteenth Reason Why";
    private static final double EXPLORATION_CONSTANT = 1.41;
    protected int[][] preferredMoves = {
            {1, 1}, //Center
            {0, 0}, {2, 2}, {0, 2}, {2, 0},  //Corners ordered across
            {0, 1}, {2, 1}, {1, 0}, {1, 2}}; //Outer Middles ordered across

    private class Node {
        final IMove move;
        final Node parent;
        final List<Node> children = new ArrayList<>();
        int visits = 0;
        double totalReward = 0;
        final IGameState state;

        public Node(IGameState state, Node parent, IMove move) {
            this.state = ThirteenthReasonWhyBot.this.cloneState(state);
            this.parent = parent;
            this.move = move;
        }
    }

    private GameSimulator createSimulator(IGameState state) {
        GameSimulator simulator = new GameSimulator(new GameState());
        simulator.setGameOver(GameOverState.Active);
        simulator.setCurrentPlayer(state.getMoveNumber() % 2);
        simulator.getCurrentState().setRoundNumber(state.getRoundNumber());
        simulator.getCurrentState().setMoveNumber(state.getMoveNumber());
        simulator.getCurrentState().getField().setBoard(state.getField().getBoard());
        simulator.getCurrentState().getField().setMacroboard(state.getField().getMacroboard());
        return simulator;
    }

    @Override
    public IMove doMove(IGameState state) {
        List<IMove> winningMoves = getWinningMoves(state);
        if (!winningMoves.isEmpty()) {
            return winningMoves.get(0);
        }

        if (state.getMoveNumber() < 3) {
            for (int[] move : preferredMoves)
            {
                if(state.getField().getMacroboard()[move[0]][move[1]].equals(IField.AVAILABLE_FIELD))
                {
                    //find move to play
                    for (int[] selectedMove : preferredMoves)
                    {
                        int x = move[0]*3 + selectedMove[0];
                        int y = move[1]*3 + selectedMove[1];
                        if(state.getField().getBoard()[x][y].equals(IField.EMPTY_FIELD))
                        {
                            return new Move(x,y);
                        }
                    }
                }
            }
        }

        return mctsMove(state);
    }

    private List<IMove> getWinningMoves(IGameState state) {
        String player = state.getMoveNumber() % 2 == 0 ? "0" : "1";
        List<IMove> avail = state.getField().getAvailableMoves();
        List<IMove> winningMoves = new ArrayList<>();
        for (IMove move : avail) {
            if (isWinningMove(state, move, player)) {
                winningMoves.add(move);
            }
        }
        return winningMoves;
    }

    private boolean isWinningMove(IGameState state, IMove move, String player){
        String[][] board = Arrays.stream(state.getField().getBoard()).map(String[]::clone).toArray(String[][]::new);

        board[move.getX()][move.getY()] = player;

        int startX = move.getX()-(move.getX()%3);
        if(board[startX][move.getY()].equals(player))
            if (board[startX][move.getY()].equals(board[startX+1][move.getY()]) &&
                    board[startX+1][move.getY()].equals(board[startX+2][move.getY()]))
                return true;

        int startY = move.getY()-(move.getY()%3);
        if(board[move.getX()][startY].equals(player))
            if (board[move.getX()][startY].equals(board[move.getX()][startY+1]) &&
                    board[move.getX()][startY+1].equals(board[move.getX()][startY+2]))
                return true;


        if(board[startX][startY].equals(player))
            if (board[startX][startY].equals(board[startX+1][startY+1]) &&
                    board[startX+1][startY+1].equals(board[startX+2][startY+2]))
                return true;

        if(board[startX][startY+2].equals(player))
            if (board[startX][startY+2].equals(board[startX+1][startY+1]) &&
                    board[startX+1][startY+1].equals(board[startX+2][startY]))
                return true;

        return false;
    }
    private IMove mctsMove(IGameState state) {
        Node root = new Node(state, null, null);
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < moveTimeMs) {
            Node node = select(root);
            GameSimulator simulator = createSimulator(node.state);
            if (simulator.getGameOver() == GameOverState.Active) {
                expand(node);
            }
            double reward = simulate(node);
            backpropagate(node, reward);
        }

        return root.children.stream()
                .max(Comparator.comparingDouble(n -> n.visits))
                .orElseThrow().move;
    }

    private Node select(Node node) {
        while (!node.children.isEmpty()) {
            Node bestChild = null;
            double bestUct = Double.NEGATIVE_INFINITY;
            List<Node> bestChildren = new ArrayList<>();

            for (Node child : node.children) {
                double uct = calculateUct(child);
                //System.out.println("UCT: " + uct + " for child " + child.move);
                if (uct > bestUct) {
                    bestUct = uct;
                    bestChildren.clear();
                    bestChildren.add(child);
                } else if (uct == bestUct) {
                    bestChildren.add(child);
                }
            }
            //System.out.println("Best UCT: " + bestUct + " for children " + bestChildren);
            Random rand = new Random();
            node = bestChildren.get(rand.nextInt(bestChildren.size()));
        }
        return node;
    }
    private IGameState cloneState(IGameState state) {
        GameSimulator simulator = createSimulator(state);
        return simulator.getCurrentState();
    }

    private double calculateUct(Node node) {
        if (node.visits == 0) return Double.MAX_VALUE;
        return (node.totalReward / node.visits)
                + EXPLORATION_CONSTANT * Math.sqrt(Math.log(node.parent.visits) / node.visits);
    }

    private void expand(Node node) {
        List<IMove> moves = node.state.getField().getAvailableMoves();
        for (IMove move : moves) {
            GameSimulator simulator = createSimulator(node.state);
            simulator.updateGame(move);
            node.children.add(new Node(simulator.getCurrentState(), node, move));
        }
    }

    private double simulate(Node node) {
        IGameState simState = cloneState(node.state);
        Random rand = new Random();

        while (true) {
            GameSimulator simulator = createSimulator(simState);
            if (simulator.getGameOver() != GameOverState.Active) {
                break;
            }

            List<IMove> moves = simState.getField().getAvailableMoves();
            if (moves.isEmpty()) {
                return 0.5; // Tie
            }


            List<IMove> winningMoves = getWinningMoves(simState);
            if (!winningMoves.isEmpty()) {
                return 1;
            }


            List<IMove> blockingMoves = getWinningMovesForOpponent(simState);
            if (!blockingMoves.isEmpty()) {
                return 0;
            }

            IMove randomMove = moves.get(rand.nextInt(moves.size()));
            simulator.updateGame(randomMove);
            simState = simulator.getCurrentState();
        }

        GameSimulator simulator = createSimulator(simState);
        if (simulator.getGameOver() == GameOverState.Win) {
            return simulator.getCurrentState().getMoveNumber() % 2 == node.state.getMoveNumber() % 2 ? 1 : 0;
        }
        return 0.5;
    }

    private List<IMove> getWinningMovesForOpponent(IGameState state) {
        String opponent = state.getMoveNumber() % 2 == 0 ? "1" : "0";
        List<IMove> avail = state.getField().getAvailableMoves();
        List<IMove> blockingMoves = new ArrayList<>();

        for (IMove move : avail) {

            String[][] board = Arrays.stream(state.getField().getBoard()).map(String[]::clone).toArray(String[][]::new);
            board[move.getX()][move.getY()] = opponent;

            if (isWinningMove(state, move, opponent)) {
                blockingMoves.add(move);
            }
        }
        return blockingMoves;
    }

    private void backpropagate(Node node, double reward) {
        while (node != null) {
            node.visits++;
            node.totalReward += reward;
            node = node.parent;
        }
    }

    /*
        The code below is a simulator for simulation of gameplay. This is needed for AI.

        It is put here to make the Bot independent of the GameManager and its subclasses/enums

        Now this class is only dependent on a few interfaces: IMove, IField, and IGameState

        You could say it is self-contained. The drawback is that if the game rules change, the simulator must be
        changed accordingly, making the code redundant.

     */

    @Override
    public String getBotName() {
        return BOT_NAME;
    }

    public enum GameOverState {
        Active,
        Win,
        Tie
    }

    public class Move implements IMove {
        int x = 0;
        int y = 0;

        public Move(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Move move = (Move) o;
            return x == move.x && y == move.y;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y);
        }
    }

    class GameSimulator {
        private final IGameState currentState;
        private int currentPlayer = 0; //player0 == 0 && player1 == 1
        private volatile GameOverState gameOver = GameOverState.Active;

        public void setGameOver(GameOverState state) {
            gameOver = state;
        }

        public GameOverState getGameOver() {
            return gameOver;
        }

        public void setCurrentPlayer(int player) {
            currentPlayer = player;
        }

        public IGameState getCurrentState() {
            return currentState;
        }

        public GameSimulator(IGameState currentState) {
            this.currentState = currentState;
        }

        public Boolean updateGame(IMove move) {
            if (!verifyMoveLegality(move))
                return false;

            updateBoard(move);
            currentPlayer = (currentPlayer + 1) % 2;

            return true;
        }

        private Boolean verifyMoveLegality(IMove move) {
            IField field = currentState.getField();
            boolean isValid = field.isInActiveMicroboard(move.getX(), move.getY());

            if (isValid && (move.getX() < 0 || 9 <= move.getX())) isValid = false;
            if (isValid && (move.getY() < 0 || 9 <= move.getY())) isValid = false;

            if (isValid && !field.getBoard()[move.getX()][move.getY()].equals(IField.EMPTY_FIELD))
                isValid = false;

            return isValid;
        }

        private void updateBoard(IMove move) {
            String[][] board = currentState.getField().getBoard();
            board[move.getX()][move.getY()] = currentPlayer + "";
            currentState.setMoveNumber(currentState.getMoveNumber() + 1);
            if (currentState.getMoveNumber() % 2 == 0) {
                currentState.setRoundNumber(currentState.getRoundNumber() + 1);
            }
            checkAndUpdateIfWin(move);
            updateMacroboard(move);

        }

        private void checkAndUpdateIfWin(IMove move) {
            String[][] macroBoard = currentState.getField().getMacroboard();
            int macroX = move.getX() / 3;
            int macroY = move.getY() / 3;

            if (macroBoard[macroX][macroY].equals(IField.EMPTY_FIELD) ||
                    macroBoard[macroX][macroY].equals(IField.AVAILABLE_FIELD)) {

                String[][] board = getCurrentState().getField().getBoard();

                if (isWin(board, move, "" + currentPlayer))
                    macroBoard[macroX][macroY] = currentPlayer + "";
                else if (isTie(board, move))
                    macroBoard[macroX][macroY] = "TIE";

                //Check macro win
                if (isWin(macroBoard, new Move(macroX, macroY), "" + currentPlayer))
                    gameOver = GameOverState.Win;
                else if (isTie(macroBoard, new Move(macroX, macroY)))
                    gameOver = GameOverState.Tie;
            }

        }

        private boolean isTie(String[][] board, IMove move) {
            int localX = move.getX() % 3;
            int localY = move.getY() % 3;
            int startX = move.getX() - (localX);
            int startY = move.getY() - (localY);

            for (int i = startX; i < startX + 3; i++) {
                for (int k = startY; k < startY + 3; k++) {
                    if (board[i][k].equals(IField.AVAILABLE_FIELD) ||
                            board[i][k].equals(IField.EMPTY_FIELD))
                        return false;
                }
            }
            return true;
        }


        public boolean isWin(String[][] board, IMove move, String currentPlayer) {
            int localX = move.getX() % 3;
            int localY = move.getY() % 3;
            int startX = move.getX() - (localX);
            int startY = move.getY() - (localY);

            //check col
            for (int i = startY; i < startY + 3; i++) {
                if (!board[move.getX()][i].equals(currentPlayer))
                    break;
                if (i == startY + 3 - 1) return true;
            }

            //check row
            for (int i = startX; i < startX + 3; i++) {
                if (!board[i][move.getY()].equals(currentPlayer))
                    break;
                if (i == startX + 3 - 1) return true;
            }

            //check diagonal
            if (localX == localY) {
                //we're on a diagonal
                int y = startY;
                for (int i = startX; i < startX + 3; i++) {
                    if (!board[i][y++].equals(currentPlayer))
                        break;
                    if (i == startX + 3 - 1) return true;
                }
            }

            //check anti diagonal
            if (localX + localY == 3 - 1) {
                int less = 0;
                for (int i = startX; i < startX + 3; i++) {
                    if (!board[i][(startY + 2) - less++].equals(currentPlayer))
                        break;
                    if (i == startX + 3 - 1) return true;
                }
            }
            return false;
        }

        private void updateMacroboard(IMove move) {
            String[][] macroBoard = currentState.getField().getMacroboard();
            for (int i = 0; i < macroBoard.length; i++)
                for (int k = 0; k < macroBoard[i].length; k++) {
                    if (macroBoard[i][k].equals(IField.AVAILABLE_FIELD))
                        macroBoard[i][k] = IField.EMPTY_FIELD;
                }

            int xTrans = move.getX() % 3;
            int yTrans = move.getY() % 3;

            if (macroBoard[xTrans][yTrans].equals(IField.EMPTY_FIELD))
                macroBoard[xTrans][yTrans] = IField.AVAILABLE_FIELD;
            else {
                // Field is already won, set all fields not won to avail.
                for (int i = 0; i < macroBoard.length; i++)
                    for (int k = 0; k < macroBoard[i].length; k++) {
                        if (macroBoard[i][k].equals(IField.EMPTY_FIELD))
                            macroBoard[i][k] = IField.AVAILABLE_FIELD;
                    }
            }
        }
    }

}
