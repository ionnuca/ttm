package md.ttm.repository;

import md.ttm.model.player.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findAllByOrderByRatingDescLastNameAscFirstNameAsc();
}
