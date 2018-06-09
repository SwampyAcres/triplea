package games.strategy.triplea.ai.pro.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.PlayerID;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.triplea.TripleAUnit;
import games.strategy.triplea.ai.AiUtils;
import games.strategy.triplea.ai.pro.ProData;
import games.strategy.triplea.ai.pro.data.ProPurchaseOption;
import games.strategy.triplea.ai.pro.data.ProTerritory;
import games.strategy.triplea.attachments.TechAttachment;
import games.strategy.triplea.attachments.UnitAttachment;
import games.strategy.triplea.attachments.UnitSupportAttachment;
import games.strategy.triplea.delegate.AirMovementValidator;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.TransportTracker;
import games.strategy.util.CollectionUtils;

/**
 * Pro AI transport utilities.
 */
public class ProTransportUtils {

  public static int findMaxMovementForTransports(final List<ProPurchaseOption> seaTransportPurchaseOptions) {
    int maxMovement = 2;
    final int maxTransportEfficiency = 0;
    for (final ProPurchaseOption ppo : seaTransportPurchaseOptions) {
      if (ppo.getTransportEfficiency() > maxTransportEfficiency) {
        maxMovement = ppo.getMovement();
      }
    }
    return maxMovement;
  }

  public static List<Unit> getUnitsToTransportThatCantMoveToHigherValue(final PlayerID player, final Unit transport,
      final Set<Territory> territoriesToLoadFrom, final List<Unit> unitsToIgnore,
      final Map<Territory, ProTerritory> moveMap, final Map<Unit, Set<Territory>> unitMoveMap, final double value) {

    final List<Unit> unitsToIgnoreOrHaveBetterLandMove = new ArrayList<>(unitsToIgnore);
    if (!TransportTracker.isTransporting(transport)) {

      // Get all units that can be transported
      final List<Unit> units = new ArrayList<>();
      for (final Territory loadFrom : territoriesToLoadFrom) {
        units.addAll(loadFrom.getUnits()
            .getMatches(ProMatches.unitIsOwnedTransportableUnitAndCanBeLoaded(player, transport, true)));
      }
      units.removeAll(unitsToIgnore);

      // Check to see which have higher land move value
      for (final Unit u : units) {
        if (unitMoveMap.get(u) != null) {
          for (final Territory t : unitMoveMap.get(u)) {
            if (moveMap.get(t) != null && moveMap.get(t).getValue() > value) {
              unitsToIgnoreOrHaveBetterLandMove.add(u);
              break;
            }
          }
        }
      }
    }
    return getUnitsToTransportFromTerritories(player, transport, territoriesToLoadFrom,
        unitsToIgnoreOrHaveBetterLandMove);
  }

  public static List<Unit> getUnitsToTransportFromTerritories(final PlayerID player, final Unit transport,
      final Set<Territory> territoriesToLoadFrom, final List<Unit> unitsToIgnore) {
    return getUnitsToTransportFromTerritories(player, transport, territoriesToLoadFrom, unitsToIgnore,
        ProMatches.unitIsOwnedTransportableUnitAndCanBeLoaded(player, transport, true));
  }

  // TODO: this needs fixed to consider whether a valid route exists to load all units
  public static List<Unit> getUnitsToTransportFromTerritories(final PlayerID player, final Unit transport,
      final Set<Territory> territoriesToLoadFrom, final List<Unit> unitsToIgnore,
      final Predicate<Unit> validUnitMatch) {
    final List<Unit> selectedUnits = new ArrayList<>();

    // Get units if transport already loaded
    if (TransportTracker.isTransporting(transport)) {
      selectedUnits.addAll(TransportTracker.transporting(transport));
    } else {

      // Get all units that can be transported
      final List<Unit> units = new ArrayList<>();
      for (final Territory loadFrom : territoriesToLoadFrom) {
        units.addAll(loadFrom.getUnits().getMatches(validUnitMatch));
      }
      units.removeAll(unitsToIgnore);

      // Sort units by attack
      units.sort(getDecreasingAttackComparator(player));

      // Get best units that can be loaded
      selectedUnits.addAll(selectUnitsToTransportFromList(transport, units));
    }
    return selectedUnits;
  }

  public static List<Unit> selectUnitsToTransportFromList(final Unit transport, final List<Unit> units) {
    final List<Unit> selectedUnits = new ArrayList<>();
    final int capacity = UnitAttachment.get(transport.getType()).getTransportCapacity();
    int capacityCount = 0;
    for (final Unit unit : units) {
      final int cost = UnitAttachment.get(unit.getType()).getTransportCost();
      if (cost <= (capacity - capacityCount)) {
        selectedUnits.add(unit);
        capacityCount += cost;
        if (capacityCount >= capacity) {
          break;
        }
      }
    }
    return selectedUnits;
  }

  public static int findUnitsTransportCost(final List<Unit> units) {
    int transportCost = 0;
    for (final Unit unit : units) {
      transportCost += UnitAttachment.get(unit.getType()).getTransportCost();
    }
    return transportCost;
  }

  public static List<Unit> getUnitsToAdd(final Unit unit, final Map<Territory, ProTerritory> moveMap) {
    return getUnitsToAdd(unit, new ArrayList<>(), moveMap);
  }

  public static List<Unit> getUnitsToAdd(final Unit unit, final List<Unit> alreadyMovedUnits,
      final Map<Territory, ProTerritory> moveMap) {
    final List<Unit> movedUnits = getMovedUnits(alreadyMovedUnits, moveMap);
    return findBestUnitsToLandTransport(unit, ProData.unitTerritoryMap.get(unit),
        movedUnits);
  }

  public static List<Unit> getMovedUnits(final List<Unit> alreadyMovedUnits,
      final Map<Territory, ProTerritory> attackMap) {
    final List<Unit> movedUnits = new ArrayList<>(alreadyMovedUnits);
    movedUnits.addAll(attackMap.values().stream()
        .map(ProTerritory::getAllDefenders)
        .flatMap(Collection::stream)
        .collect(Collectors.toList()));
    return movedUnits;
  }

  public static List<Unit> findBestUnitsToLandTransport(final Unit unit, final Territory t) {
    return findBestUnitsToLandTransport(unit, t, new ArrayList<>());
  }

  /**
   * Check if unit is can land transport and if there are any unused units that could be transported.
   */
  public static List<Unit> findBestUnitsToLandTransport(final Unit unit, final Territory t,
      final List<Unit> usedUnits) {
    if (usedUnits.contains(unit)) {
      return new ArrayList<>();
    }
    final PlayerID player = unit.getOwner();
    final List<Unit> units = t.getUnits().getMatches(Matches.unitIsOwnedBy(player)
        .and(Matches.unitIsLandTransportable()).and(ProMatches.unitHasLessMovementThan(unit)));
    units.removeAll(usedUnits);
    if (Matches.unitIsLandTransport().negate().test(unit) || !TechAttachment.isMechanizedInfantry(player)
        || units.isEmpty()) {
      return Collections.singletonList(unit);
    }
    units.sort(Comparator.<Unit>comparingInt(u -> TripleAUnit.get(u).getMovementLeft())
        .thenComparing(getDecreasingAttackComparator(player)));
    final List<Unit> results = new ArrayList<Unit>();
    results.add(unit);
    if (Matches.unitIsLandTransportWithoutCapacity().test(unit)) {
      results.add(units.get(0));
    } else {
      results.addAll(selectUnitsToTransportFromList(unit, units));
    }
    return results;
  }

  private static Comparator<Unit> getDecreasingAttackComparator(final PlayerID player) {
    return (o1, o2) -> {

      // Very rough way to add support power
      final Set<UnitSupportAttachment> supportAttachments1 = UnitSupportAttachment.get(o1.getType());
      int maxSupport1 = 0;
      for (final UnitSupportAttachment usa : supportAttachments1) {
        if (usa.getAllied() && usa.getOffence() && usa.getBonus() > maxSupport1) {
          maxSupport1 = usa.getBonus();
        }
      }
      final int attack1 = UnitAttachment.get(o1.getType()).getAttack(player) + maxSupport1;
      final Set<UnitSupportAttachment> supportAttachments2 = UnitSupportAttachment.get(o2.getType());
      int maxSupport2 = 0;
      for (final UnitSupportAttachment usa : supportAttachments2) {
        if (usa.getAllied() && usa.getOffence() && usa.getBonus() > maxSupport2) {
          maxSupport2 = usa.getBonus();
        }
      }
      final int attack2 = UnitAttachment.get(o2.getType()).getAttack(player) + maxSupport2;
      return attack2 - attack1;
    };
  }

  public static List<Unit> getAirThatCantLandOnCarrier(final PlayerID player, final Territory t,
      final List<Unit> units) {
    final GameData data = ProData.getData();

    int capacity = AirMovementValidator.carrierCapacity(units, t);
    final Collection<Unit> airUnits = CollectionUtils.getMatches(units, ProMatches.unitIsAlliedAir(player, data));
    final List<Unit> airThatCantLand = new ArrayList<>();
    for (final Unit airUnit : airUnits) {
      final UnitAttachment ua = UnitAttachment.get(airUnit.getType());
      final int cost = ua.getCarrierCost();
      if (cost != -1) {
        if (cost <= capacity) {
          capacity -= cost;
        } else {
          airThatCantLand.add(airUnit);
        }
      }
    }
    return airThatCantLand;
  }

  public static boolean validateCarrierCapacity(final PlayerID player, final Territory t,
      final List<Unit> existingUnits, final Unit newUnit) {
    final GameData data = ProData.getData();

    int capacity = AirMovementValidator.carrierCapacity(existingUnits, t);
    final Collection<Unit> airUnits =
        CollectionUtils.getMatches(existingUnits, ProMatches.unitIsAlliedAir(player, data));
    airUnits.add(newUnit);
    for (final Unit airUnit : airUnits) {
      final UnitAttachment ua = UnitAttachment.get(airUnit.getType());
      final int cost = ua.getCarrierCost();
      if (cost != -1) {
        capacity -= cost;
      }
    }
    return capacity >= 0;
  }

  public static int getUnusedLocalCarrierCapacity(final PlayerID player, final Territory t,
      final List<Unit> unitsToPlace) {
    final GameData data = ProData.getData();

    // Find nearby carrier capacity
    final Set<Territory> nearbyTerritories =
        data.getMap().getNeighbors(t, 2, ProMatches.territoryCanMoveAirUnits(player, data, false));
    nearbyTerritories.add(t);
    final List<Unit> ownedNearbyUnits = new ArrayList<>();
    int capacity = 0;
    for (final Territory nearbyTerritory : nearbyTerritories) {
      final List<Unit> units = nearbyTerritory.getUnits().getMatches(Matches.unitIsOwnedBy(player));
      if (nearbyTerritory.equals(t)) {
        units.addAll(unitsToPlace);
      }
      ownedNearbyUnits.addAll(units);
      capacity += AirMovementValidator.carrierCapacity(units, t);
    }

    // Find nearby air unit carrier cost
    final Collection<Unit> airUnits = CollectionUtils.getMatches(ownedNearbyUnits, ProMatches.unitIsOwnedAir(player));
    for (final Unit airUnit : airUnits) {
      final UnitAttachment ua = UnitAttachment.get(airUnit.getType());
      final int cost = ua.getCarrierCost();
      if (cost != -1) {
        capacity -= cost;
      }
    }
    return capacity;
  }

  public static int getUnusedCarrierCapacity(final PlayerID player, final Territory t, final List<Unit> unitsToPlace) {
    final List<Unit> units = new ArrayList<>(unitsToPlace);
    units.addAll(t.getUnits().getUnits());
    int capacity = AirMovementValidator.carrierCapacity(units, t);
    final Collection<Unit> airUnits = CollectionUtils.getMatches(units, ProMatches.unitIsOwnedAir(player));
    for (final Unit airUnit : airUnits) {
      final UnitAttachment ua = UnitAttachment.get(airUnit.getType());
      final int cost = ua.getCarrierCost();
      if (cost != -1) {
        capacity -= cost;
      }
    }
    return capacity;
  }

  public static List<Unit> interleaveUnitsCarriersAndPlanes(final List<Unit> units,
      final int planesThatDontNeedToLand) {
    if (units.stream().noneMatch(Matches.unitIsCarrier())
        || units.stream().noneMatch(Matches.unitCanLandOnCarrier())) {
      return units;
    }

    // Clone the current list
    final ArrayList<Unit> result = new ArrayList<>(units);
    Unit seekedCarrier = null;
    int indexToPlaceCarrierAt = -1;
    int spaceLeftOnSeekedCarrier = -1;
    int processedPlaneCount = 0;
    final List<Unit> filledCarriers = new ArrayList<>();

    // Loop through all units, starting from the right, and rearrange units
    for (int i = result.size() - 1; i >= 0; i--) {
      final Unit unit = result.get(i);
      final UnitAttachment ua = UnitAttachment.get(unit.getType());
      if (ua.getCarrierCost() > 0 || i == 0) { // If this is a plane or last unit
        // If we haven't ignored enough trailing planes and not last unit
        if (processedPlaneCount < planesThatDontNeedToLand && i > 0) {
          processedPlaneCount++; // Increase number of trailing planes ignored
          continue; // And skip any processing
        }

        // If this is the first carrier seek and not last unit
        if (seekedCarrier == null && i > 0) {
          final int seekedCarrierIndex = AiUtils.getIndexOfLastUnitMatching(result,
              Matches.unitIsCarrier().and(Matches.isNotInList(filledCarriers)), result.size() - 1);
          if (seekedCarrierIndex == -1) {
            break; // No carriers left
          }
          seekedCarrier = result.get(seekedCarrierIndex);
          indexToPlaceCarrierAt = i + 1; // Tell the code to insert carrier to the right of this plane
          spaceLeftOnSeekedCarrier = UnitAttachment.get(seekedCarrier.getType()).getCarrierCapacity();
        }
        if (ua.getCarrierCost() > 0) {
          spaceLeftOnSeekedCarrier -= ua.getCarrierCost();
        }

        // If the carrier has been filled or overflowed or last unit
        if (indexToPlaceCarrierAt > 0 && (spaceLeftOnSeekedCarrier <= 0 || i == 0)) {
          if (spaceLeftOnSeekedCarrier < 0) {
            i++; // Increment current unit index, so we re-process this unit (since it can't fit on the current carrier)
          }

          // If the seeked carrier is earlier in the list
          if (result.indexOf(seekedCarrier) < i) {

            // Move the carrier up to the planes by: removing carrier, then reinserting it
            // (index decreased cause removal of carrier reduced indexes)
            result.remove(seekedCarrier);
            result.add(indexToPlaceCarrierAt - 1, seekedCarrier);
            i--; // We removed carrier in earlier part of list, so decrease index
            filledCarriers.add(seekedCarrier);

            // Find the next carrier
            seekedCarrier = AiUtils.getLastUnitMatching(result,
                Matches.unitIsCarrier().and(Matches.isNotInList(filledCarriers)), result.size() - 1);
            if (seekedCarrier == null) {
              break; // No carriers left
            }

            // Place next carrier right before this plane (which just filled the old carrier that was just moved)
            indexToPlaceCarrierAt = i;
            spaceLeftOnSeekedCarrier = UnitAttachment.get(seekedCarrier.getType()).getCarrierCapacity();
          } else {

            // If it's later in the list
            final int oldIndex = result.indexOf(seekedCarrier);
            int carrierPlaceLocation = indexToPlaceCarrierAt;

            // Place carrier where it's supposed to go
            result.remove(seekedCarrier);
            if (oldIndex < indexToPlaceCarrierAt) {
              carrierPlaceLocation--;
            }
            result.add(carrierPlaceLocation, seekedCarrier);
            filledCarriers.add(seekedCarrier);

            // Move the planes down to the carrier
            final List<Unit> planesBetweenHereAndCarrier = new ArrayList<>();
            for (int i2 = i; i2 < carrierPlaceLocation; i2++) {
              final Unit unit2 = result.get(i2);
              final UnitAttachment ua2 = UnitAttachment.get(unit2.getType());
              if (ua2.getCarrierCost() > 0) {
                planesBetweenHereAndCarrier.add(unit2);
              }
            }
            Collections.reverse(planesBetweenHereAndCarrier); // Invert list, so they are inserted in the same order
            int planeMoveCount = 0;
            for (final Unit plane : planesBetweenHereAndCarrier) {
              result.remove(plane);

              // Insert each plane right before carrier (index decreased cause removal of carrier reduced indexes)
              result.add(carrierPlaceLocation - 1, plane);
              planeMoveCount++;
            }

            // Find the next carrier
            seekedCarrier = AiUtils.getLastUnitMatching(result,
                Matches.unitIsCarrier().and(Matches.isNotInList(filledCarriers)), result.size() - 1);
            if (seekedCarrier == null) {
              break; // No carriers left
            }

            // Since we only moved planes up, just reduce next carrier place index by plane move count
            indexToPlaceCarrierAt = carrierPlaceLocation - planeMoveCount;
            spaceLeftOnSeekedCarrier = UnitAttachment.get(seekedCarrier.getType()).getCarrierCapacity();
          }
        }
      }
    }
    return result;
  }
}